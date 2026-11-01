package com.penseprecifique.api.orcamento.descritivo;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.catalogo.ItemCatalogoRepository;
import com.penseprecifique.api.cliente.ClienteService;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.produto.ProdutoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.ItemCatalogo;
import com.penseprecifique.api.shared.domain.entity.Orcamento;
import com.penseprecifique.api.shared.domain.entity.OrcamentoDescritivoOpcao;
import com.penseprecifique.api.shared.domain.entity.Produto;
import com.penseprecifique.api.shared.domain.entity.Usuario;
import com.penseprecifique.api.shared.domain.enums.PapelCadastro;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoOrcamento;
import com.penseprecifique.api.shared.dto.request.orcamento.DescritivoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.DescritivoStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.GerarVendaDescritivoRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoItemRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.OrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.DescritivoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.DescritivoResumoResponse;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.NomeMarca;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * V0.19.0 #382 (RN-NOVA-44, DT-NOVA-10, UC-NOVO-5) — orçamento Descritivo (cardápio): mesma entidade e mesmo
 * {@code ORC-N} do orçamento de Venda, com tipo próprio, de 1 a 100 opções com preço de referência e status
 * RASCUNHO / ENVIADO / CANCELADO. Nunca gera lançamento, parcela, recibo nem baixa de estoque. "Gerar orçamento de
 * Venda" cria uma Venda em RASCUNHO pelo caminho normal do {@link OrcamentoService#criar}, com o preço de venda do
 * cadastro no momento (ORC-001), nunca o preço de referência. Serviço à parte para não inflar o {@code OrcamentoService}.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class OrcamentoDescritivoService {

    public static final int MAX_OPCOES = 100;
    private static final String MSG_LIMITE = "Informe de 1 a 100 opções.";
    private static final int TAMANHO_MAX_PAGINA = 100;

    private final Clock clock;
    private final OrcamentoDescritivoRepository repository;
    private final OrcamentoDescritivoOpcaoRepository opcaoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ClienteService clienteService;
    private final ProdutoRepository produtoRepository;
    private final ItemCatalogoRepository itemCatalogoRepository;
    private final OrcamentoService orcamentoService;

    public DescritivoResponse criar(DescritivoRequest request) {
        Usuario usuario = getUsuario();
        validarLimite(request.opcoes());
        Cliente cliente = request.clienteId() == null ? null
                : clienteService.resolverParaVinculo(request.clienteId(), usuario.getId(), PapelCadastro.CLIENTE, null);
        List<OrcamentoDescritivoOpcao> opcoes = montarOpcoes(request.opcoes(), usuario);

        usuarioRepository.lockPorId(usuario.getId());
        Integer numero = repository.findTopNumero(usuario.getId());
        Orcamento orcamento = Orcamento.builder()
                .usuario(usuario)
                .cliente(cliente)
                .tipo(TipoOrcamento.DESCRITIVO)
                .titulo(request.titulo().trim())
                .numero(numero == null ? 1 : numero + 1)
                .status(StatusOrcamento.RASCUNHO)
                .observacoes(textoOuNulo(request.observacoes()))
                .build();
        orcamento = repository.save(orcamento);
        gravarOpcoes(orcamento, opcoes);
        return montar(orcamento);
    }

    public DescritivoResponse editar(UUID id, DescritivoRequest request) {
        Usuario usuario = getUsuario();
        Orcamento orcamento = buscarEntidade(id, usuario.getId());
        if (orcamento.getStatus() != StatusOrcamento.RASCUNHO) {
            throw new BusinessException("Só é possível editar um orçamento em Rascunho.");
        }
        validarLimite(request.opcoes());
        UUID idSalvo = orcamento.getCliente() != null ? orcamento.getCliente().getId() : null;
        Cliente cliente = request.clienteId() == null ? null
                : clienteService.resolverParaVinculo(request.clienteId(), usuario.getId(), PapelCadastro.CLIENTE, idSalvo);
        List<OrcamentoDescritivoOpcao> opcoes = montarOpcoes(request.opcoes(), usuario);

        orcamento.setCliente(cliente);
        orcamento.setTitulo(request.titulo().trim());
        orcamento.setObservacoes(textoOuNulo(request.observacoes()));
        orcamento = repository.save(orcamento);
        opcaoRepository.deleteByOrcamentoId(orcamento.getId());
        gravarOpcoes(orcamento, opcoes);
        return montar(orcamento);
    }

    @Transactional(readOnly = true)
    public DescritivoResponse buscar(UUID id) {
        return montar(buscarEntidade(id, getUsuario().getId()));
    }

    @Transactional(readOnly = true)
    public Page<DescritivoResumoResponse> listar(StatusOrcamento status, String busca, Pageable pageable) {
        UUID usuarioId = getUsuario().getId();
        String buscaNormalizada = busca != null && !busca.isBlank() ? busca.trim() : null;
        Pageable paginacao = PageRequest.of(Math.max(pageable.getPageNumber(), 0),
                Math.min(Math.max(pageable.getPageSize(), 1), TAMANHO_MAX_PAGINA));
        Page<Orcamento> pagina = repository.buscar(usuarioId, status, buscaNormalizada, paginacao);
        Map<UUID, Long> contagens = pagina.isEmpty() ? Map.of()
                : opcaoRepository.contarPorOrcamento(pagina.getContent().stream().map(Orcamento::getId).toList()).stream()
                        .collect(Collectors.toMap(l -> (UUID) l[0], l -> (Long) l[1]));
        return pagina.map(o -> new DescritivoResumoResponse(o.getId(), o.getNumero(),
                IdentificadorFormatter.formatar("ORC", o.getNumero()), o.getTitulo(),
                o.getCliente() != null ? o.getCliente().getNome() : null, o.getStatus(),
                contagens.getOrDefault(o.getId(), 0L).intValue(), o.getCreatedAt()));
    }

    /** RASCUNHO → ENVIADO (grava a data de envio uma única vez) e RASCUNHO/ENVIADO → CANCELADO. Sem aprovação. */
    public DescritivoResponse mudarStatus(UUID id, DescritivoStatusRequest request) {
        Orcamento orcamento = buscarEntidade(id, getUsuario().getId());
        StatusOrcamento atual = orcamento.getStatus();
        StatusOrcamento novo = request.status();
        if (novo == StatusOrcamento.ENVIADO && atual == StatusOrcamento.RASCUNHO) {
            if (orcamento.getDataEnvio() == null) {
                orcamento.setDataEnvio(LocalDateTime.now(clock));
            }
        } else if (novo == StatusOrcamento.CANCELADO && (atual == StatusOrcamento.RASCUNHO || atual == StatusOrcamento.ENVIADO)) {
            orcamento.setDataCancelamento(LocalDateTime.now(clock));
        } else if (novo == atual) {
            throw new BusinessException("O orçamento já está nesse status.");
        } else {
            throw new BusinessException("O orçamento descritivo só pode ser enviado ou cancelado; ele não tem aprovação.");
        }
        orcamento.setStatus(novo);
        return montar(repository.save(orcamento));
    }

    /**
     * "Gerar orçamento de Venda" (CEN-NOVO-49/57). O descritivo original não muda. Sem cliente (nem no descritivo, nem
     * escolhido agora) é BLOQUEIO e nada é criado, porque a Venda exige cliente (RN-NOVA-37).
     */
    public OrcamentoDetalheResponse gerarVenda(UUID id, GerarVendaDescritivoRequest request) {
        Usuario usuario = getUsuario();
        Orcamento descritivo = buscarEntidade(id, usuario.getId());
        if (descritivo.getStatus() == StatusOrcamento.CANCELADO) {
            throw new BusinessException("Este orçamento descritivo está cancelado.");
        }
        List<GerarVendaDescritivoRequest.Escolha> escolhas = request.itens();
        if (escolhas == null || escolhas.isEmpty()) {
            throw new BusinessException("Marque ao menos uma opção.");
        }
        if (escolhas.size() > MAX_OPCOES) {
            throw new BusinessException(MSG_LIMITE);
        }
        UUID clienteId = request.clienteId() != null ? request.clienteId()
                : descritivo.getCliente() != null ? descritivo.getCliente().getId() : null;
        if (clienteId == null) {
            throw new BusinessException("Escolha o cliente para gerar o orçamento.");
        }

        Map<UUID, OrcamentoDescritivoOpcao> opcoes = opcaoRepository.findByOrcamentoIdOrderByOrdemAsc(descritivo.getId()).stream()
                .collect(Collectors.toMap(OrcamentoDescritivoOpcao::getId, Function.identity()));
        Set<UUID> vistas = new HashSet<>();
        List<OrcamentoItemRequest> itens = new ArrayList<>();
        for (GerarVendaDescritivoRequest.Escolha escolha : escolhas) {
            if (escolha == null || escolha.opcaoId() == null) {
                throw new BusinessException("Informe a opção.");
            }
            OrcamentoDescritivoOpcao opcao = opcoes.get(escolha.opcaoId());
            if (opcao == null) {
                throw new ResourceNotFoundException("Opção não encontrada");
            }
            if (!vistas.add(opcao.getId())) {
                throw new BusinessException("Há uma opção repetida na seleção.");
            }
            itens.add(itemDaVenda(opcao, escolha.quantidade()));
        }

        OrcamentoRequest venda = new OrcamentoRequest();
        venda.setClienteId(clienteId);
        venda.setItens(itens);
        venda.setTemPrazoProducao(false);
        return orcamentoService.criar(venda);
    }

    // ---------------------------------------------------------------- internos

    /** Preço do momento: o item de catálogo usa o preço de venda atual (o serviço da Venda lê ao criar); o produto avulso leva o atual aqui. */
    private OrcamentoItemRequest itemDaVenda(OrcamentoDescritivoOpcao opcao, Integer quantidade) {
        OrcamentoItemRequest item = new OrcamentoItemRequest();
        item.setQuantidade(quantidade);
        if (opcao.getItemCatalogo() != null) {
            item.setItemCatalogoId(opcao.getItemCatalogo().getId());
        } else {
            Produto produto = opcao.getProduto();
            BigDecimal atual = produto.getPrecoVenda();
            if (atual == null || atual.signum() <= 0) {
                throw new BusinessException("O produto '" + NomeMarca.rotulo(produto.getNome(), produto.getMarca())
                        + "' está sem preço de venda. Defina o preço no cadastro antes de gerar o orçamento.");
            }
            item.setProdutoId(produto.getId());
            item.setPrecoUnitario(atual.setScale(2, RoundingMode.HALF_UP));
        }
        return item;
    }

    private void validarLimite(List<DescritivoRequest.Opcao> opcoes) {
        if (opcoes == null || opcoes.isEmpty() || opcoes.size() > MAX_OPCOES) {
            throw new BusinessException(MSG_LIMITE);
        }
    }

    private List<OrcamentoDescritivoOpcao> montarOpcoes(List<DescritivoRequest.Opcao> pedidas, Usuario usuario) {
        List<OrcamentoDescritivoOpcao> opcoes = new ArrayList<>();
        int ordem = 0;
        for (DescritivoRequest.Opcao pedida : pedidas) {
            if (pedida == null || (pedida.produtoId() == null) == (pedida.itemCatalogoId() == null)) {
                throw new BusinessException("Cada opção deve ter exatamente uma origem: produto ou item de catálogo.");
            }
            Produto produto = null;
            ItemCatalogo item = null;
            String nome;
            BigDecimal precoAtual;
            if (pedida.produtoId() != null) {
                produto = produtoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(pedida.produtoId(), usuario.getId())
                        .orElseThrow(() -> new ResourceNotFoundException("Produto não encontrado"));
                nome = NomeMarca.rotulo(produto.getNome(), produto.getMarca());
                precoAtual = produto.getPrecoVenda();
            } else {
                item = itemCatalogoRepository.findByIdAndDeletedAtIsNull(pedida.itemCatalogoId())
                        .filter(i -> i.getCatalogo().getUsuario().getId().equals(usuario.getId()))
                        .orElseThrow(() -> new ResourceNotFoundException("Item de catálogo não encontrado"));
                nome = NomeMarca.rotulo(item.getNome(), item.getMarca());
                precoAtual = item.getPrecoVenda();
            }
            BigDecimal preco = pedida.precoReferencia() != null ? pedida.precoReferencia() : precoAtual;
            if (preco == null) {
                throw new BusinessException("Informe o preço de referência de '" + nome + "'.");
            }
            opcoes.add(OrcamentoDescritivoOpcao.builder()
                    .usuario(usuario)
                    .produto(produto)
                    .itemCatalogo(item)
                    .precoReferencia(preco.setScale(2, RoundingMode.HALF_UP))
                    .quantidadeUnidade(textoOuNulo(pedida.quantidadeUnidade()))
                    .ordem(ordem++)
                    .build());
        }
        return opcoes;
    }

    private void gravarOpcoes(Orcamento orcamento, List<OrcamentoDescritivoOpcao> opcoes) {
        opcoes.forEach(o -> o.setOrcamento(orcamento));
        opcaoRepository.saveAll(opcoes);
    }

    private DescritivoResponse montar(Orcamento o) {
        List<DescritivoResponse.Opcao> opcoes = opcaoRepository.findByOrcamentoIdOrderByOrdemAsc(o.getId()).stream()
                .map(op -> {
                    boolean ehProduto = op.getProduto() != null;
                    String nome = ehProduto ? NomeMarca.rotulo(op.getProduto().getNome(), op.getProduto().getMarca())
                            : NomeMarca.rotulo(op.getItemCatalogo().getNome(), op.getItemCatalogo().getMarca());
                    BigDecimal atual = ehProduto ? op.getProduto().getPrecoVenda() : op.getItemCatalogo().getPrecoVenda();
                    return new DescritivoResponse.Opcao(op.getId(),
                            ehProduto ? op.getProduto().getId() : null,
                            ehProduto ? null : op.getItemCatalogo().getId(),
                            nome, op.getPrecoReferencia(), atual, op.getQuantidadeUnidade(), op.getOrdem());
                }).toList();
        return new DescritivoResponse(o.getId(), o.getNumero(), IdentificadorFormatter.formatar("ORC", o.getNumero()),
                o.getTipo().name(), o.getTitulo(), o.getStatus(),
                o.getCliente() != null ? o.getCliente().getId() : null,
                o.getCliente() != null ? o.getCliente().getNome() : null,
                o.getObservacoes(), o.getDataEnvio(), o.getCreatedAt(), o.getUpdatedAt(), opcoes);
    }

    private Orcamento buscarEntidade(UUID id, UUID usuarioId) {
        return repository.buscarDescritivo(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));
    }

    private static String textoOuNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private Usuario getUsuario() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException("Usuário autenticado não encontrado"));
    }
}
