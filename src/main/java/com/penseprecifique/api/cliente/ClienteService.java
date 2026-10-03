package com.penseprecifique.api.cliente;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.domain.enums.TipoPessoa;
import com.penseprecifique.api.shared.dto.request.cliente.ClienteRequest;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteContagensResponse;
import com.penseprecifique.api.shared.dto.response.cliente.ClienteResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.mapper.ClienteMapper;
import com.penseprecifique.api.shared.validation.DocumentoFiscal;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NumeroSequencialUtil;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cadastro único Clientes e Fornecedores (V0.15.0, Epic #437). Classe concreta desde a V0.15.0
 * (era ClienteService + ClienteServiceImpl, migrada para a convenção do projeto).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ClienteService {

    // #354 — allowlist explícita dos campos de ordenação aceitos em GET /clientes. Campo fora desta
    // lista é rejeitado com BusinessException (400) por PageableOrdenacaoResolver, nunca mais
    // repassado cru pro Hibernate (UnknownPathException → 500).
    private static final Map<String, String> CAMPOS_ORDENACAO_CLIENTE = Map.of(
            "nome", "nome",
            "numero", "numero",
            "email", "email",
            "createdAt", "createdAt",
            "documento", "documento" // #582 (adendo 2) — ordenar por CPF/CNPJ
    );

    static final String MSG_SEM_PAPEL = "Marque se é Cliente, Fornecedor ou os dois.";

    private final ClienteRepository clienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteMapper clienteMapper;

    /**
     * #536/#538 — {@code ativo} nulo = só ativos (padrão seguro para os seletores); {@code false} =
     * só inativos. {@code papel} nulo = os dois papéis.
     */
    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(String busca, Boolean ativo, PapelCadastro papel, Pageable pageable) {
        return listar(busca, ativo, papel, false, pageable);
    }

    /**
     * #583/RN-NOVA-40 (adendo 2) — {@code incluirInativos}: ativos e inativos do papel, ativos primeiro
     * (seletores mostram o inativo riscado, sem poder escolher); ignora {@code ativo}.
     */
    @Transactional(readOnly = true)
    public Page<ClienteResponse> listar(String busca, Boolean ativo, PapelCadastro papel, boolean incluirInativos,
                                        Pageable pageable) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Pageable pageableOrdenado = PageableOrdenacaoResolver.resolver(pageable, CAMPOS_ORDENACAO_CLIENTE,
                "nome, numero, email, createdAt, documento");
        if (incluirInativos) {
            pageableOrdenado = PageRequest.of(pageableOrdenado.getPageNumber(), pageableOrdenado.getPageSize(),
                    Sort.by(Sort.Direction.DESC, "ativa").and(pageableOrdenado.getSort()));
        }

        String buscaNormalizada = (busca != null && !busca.isBlank()) ? busca.trim() : null;
        // Documento só entra na busca quando o texto tem algum dígito (evita casar "ANA" com CNPJ
        // alfanumérico); comparação sem máscara, como o documento é guardado.
        String buscaDocumento = (buscaNormalizada != null && buscaNormalizada.matches(".*\\d.*"))
                ? DocumentoFiscal.normalizar(buscaNormalizada) : null;

        Page<Cliente> pagina = clienteRepository.buscarComFiltros(usuarioId, buscaNormalizada, buscaDocumento,
                ativo == null || ativo,
                incluirInativos,
                papel == PapelCadastro.CLIENTE,
                papel == PapelCadastro.FORNECEDOR,
                pageableOrdenado);

        Page<ClienteResponse> mapeado = pagina.map(clienteMapper::toResponse);
        return new PageImpl<>(mapeado.getContent(), pageable, mapeado.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ClienteContagensResponse contagens() {
        UUID usuarioId = getUsuarioIdAutenticado();
        return new ClienteContagensResponse(
                clienteRepository.countByUsuarioIdAndAtiva(usuarioId, true),
                clienteRepository.countByUsuarioIdAndAtivaTrueAndEhClienteTrue(usuarioId),
                clienteRepository.countByUsuarioIdAndAtivaTrueAndEhFornecedorTrue(usuarioId),
                clienteRepository.countByUsuarioIdAndAtiva(usuarioId, false));
    }

    /** "Exibir vínculo existente" (DT-NOVA-2): encontra inativos e qualquer papel. */
    @Transactional(readOnly = true)
    public ClienteResponse buscarPorId(UUID id) {
        return clienteMapper.toResponse(buscarEntidade(id, getUsuarioIdAutenticado()));
    }

    public ClienteResponse cadastrar(ClienteRequest request) {
        Usuario usuario = getUsuarioAutenticado();
        String documento = validarPapeisEDocumento(request, usuario.getId(), null);
        Cliente cliente = clienteMapper.toEntity(request, usuario);
        cliente.setDocumento(documento);
        // #161 — lockPorId serializa por usuario_id antes de ler o MAX(numero), evitando race condition.
        usuarioRepository.lockPorId(usuario.getId());
        cliente.setNumero(NumeroSequencialUtil.proximoNumero(
                clienteRepository.findTopByUsuarioIdOrderByNumeroDesc(usuario.getId()).map(Cliente::getNumero)));
        return clienteMapper.toResponse(clienteRepository.save(cliente));
    }

    /**
     * RN-NOVA-1 — papéis podem ser marcados/desmarcados a qualquer momento, inclusive com histórico:
     * orçamentos, vendas e compras existentes não mudam (DT-NOVA-2, vínculo existente).
     */
    public ClienteResponse editar(UUID id, ClienteRequest request) {
        UUID usuarioId = getUsuarioIdAutenticado();
        Cliente cliente = buscarEntidade(id, usuarioId);
        String documento = validarPapeisEDocumento(request, usuarioId, cliente.getId());
        clienteMapper.updateEntity(request, cliente);
        cliente.setDocumento(documento);
        return clienteMapper.toResponse(clienteRepository.save(cliente));
    }

    /** #538/RN-NOVA-2 — reversível, só alterna {@code ativa}; o registro continua na lista. */
    public void inativar(UUID id) {
        Cliente cliente = buscarEntidade(id, getUsuarioIdAutenticado());
        cliente.setAtiva(false);
        clienteRepository.save(cliente);
    }

    public void reativar(UUID id) {
        Cliente cliente = buscarEntidade(id, getUsuarioIdAutenticado());
        cliente.setAtiva(true);
        clienteRepository.save(cliente);
    }

    /**
     * RN-NOVA-3 / DT-NOVA-2 — resolve o cliente/fornecedor escolhido num vínculo (orçamento, venda do
     * Caixa, compra). A trava de ativo e de papel só vale quando o vínculo é novo: se {@code id} é o
     * mesmo já salvo ({@code idSalvo}), o registro é aceito mesmo inativo ou sem o papel.
     */
    @Transactional(readOnly = true)
    public Cliente resolverParaVinculo(UUID id, UUID usuarioId, PapelCadastro papel, UUID idSalvo) {
        Cliente cliente = buscarEntidade(id, usuarioId);
        if (Objects.equals(id, idSalvo)) {
            return cliente;
        }
        boolean temPapel = papel == PapelCadastro.CLIENTE
                ? Boolean.TRUE.equals(cliente.getEhCliente())
                : Boolean.TRUE.equals(cliente.getEhFornecedor());
        if (!Boolean.TRUE.equals(cliente.getAtiva()) || !temPapel) {
            throw BusinessException.explicado("Cadastro indisponível", papel == PapelCadastro.CLIENTE
                    ? "Este cadastro não está ativo como Cliente."
                    : "Este cadastro não está ativo como Fornecedor.",
                    "Só cadastros ativos e com o papel certo entram em registros novos.",
                    "Em Clientes e Fornecedores, reative o cadastro ou marque o papel que falta.");
        }
        return cliente;
    }

    private Cliente buscarEntidade(UUID id, UUID usuarioId) {
        return clienteRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cadastro não encontrado: " + id));
    }

    /**
     * RN-NOVA-1 (papel obrigatório) e RN-NOVA-17 (documento). Devolve o documento normalizado, ou
     * nulo quando não informado.
     */
    private String validarPapeisEDocumento(ClienteRequest request, UUID usuarioId, UUID idAtual) {
        if (!Boolean.TRUE.equals(request.getEhCliente()) && !Boolean.TRUE.equals(request.getEhFornecedor())) {
            throw BusinessException.explicado("Papel do cadastro", MSG_SEM_PAPEL,
                    "O papel define onde o cadastro aparece: como cliente no Orçamento e no Caixa, como fornecedor nas Compras.",
                    "Marque Cliente, Fornecedor ou os dois.");
        }

        TipoPessoa tipo = request.getTipoPessoa() != null ? request.getTipoPessoa() : TipoPessoa.FISICA;
        String documento = tipo == TipoPessoa.ESTRANGEIRO
                ? textoLivreNormalizado(request.getDocumento())
                : DocumentoFiscal.normalizar(request.getDocumento());
        if (documento == null) {
            return null;
        }
        if (tipo == TipoPessoa.FISICA && !DocumentoFiscal.cpfValido(documento)) {
            throw BusinessException.explicado("CPF inválido", "CPF inválido",
                    "Os dois últimos dígitos do CPF são calculados a partir dos outros; o número digitado não fecha essa conta.",
                    "Confira o CPF no documento (11 dígitos, ex.: 529.982.247-25) ou deixe o campo vazio.");
        }
        if (tipo == TipoPessoa.JURIDICA && !DocumentoFiscal.cnpjValido(documento)) {
            throw BusinessException.explicado("CNPJ inválido", "CNPJ inválido",
                    "Os dois últimos dígitos do CNPJ são calculados a partir dos outros; o número digitado não fecha essa conta.",
                    "Confira o CNPJ no cartão da empresa (14 caracteres, podendo ter letras, ex.: 12.ABC.345/01DE-35) ou deixe o campo vazio.");
        }

        clienteRepository.findByUsuarioIdAndDocumento(usuarioId, documento)
                .filter(existente -> !existente.getId().equals(idAtual))
                .ifPresent(existente -> {
                    throw BusinessException.explicado("Documento já cadastrado", "Já existe um cadastro com este CPF/CNPJ: "
                            + IdentificadorFormatter.formatar("CLI", existente.getNumero())
                            + " — " + existente.getNome(),
                            "Cada CPF/CNPJ pertence a um cadastro só, para o histórico da pessoa ou empresa não se dividir.",
                            "Abra o cadastro existente; se ele for cliente e agora também fornecedor, marque os dois papéis nele.");
                });
        return documento;
    }

    // Estrangeiro: sem validação de dígito; só espaços nas pontas e maiúsculo, para a comparação de
    // duplicidade ignorar maiúscula/minúscula como no CPF/CNPJ.
    private static String textoLivreNormalizado(String documento) {
        if (documento == null || documento.isBlank()) {
            return null;
        }
        return documento.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private UUID getUsuarioIdAutenticado() {
        return getUsuarioAutenticado().getId();
    }

    private Usuario getUsuarioAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
