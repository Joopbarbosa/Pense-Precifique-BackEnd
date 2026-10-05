package com.penseprecifique.api.compra.nota;

import com.penseprecifique.api.cliente.ClienteRepository;
import com.penseprecifique.api.compra.CompraService;
import com.penseprecifique.api.insumo.InsumoRepository;
import com.penseprecifique.api.shared.domain.entity.Cliente;
import com.penseprecifique.api.shared.domain.entity.Insumo;
import com.penseprecifique.api.shared.domain.entity.VinculoItemNota;
import com.penseprecifique.api.shared.domain.enums.OrigemVinculoItemNota;
import com.penseprecifique.api.shared.dto.request.compra.IgnorarVinculoNotaRequest;
import com.penseprecifique.api.shared.dto.request.compra.VinculoNotaRequest;
import com.penseprecifique.api.shared.dto.response.compra.NotaLeituraResponse.InsumoProposto;
import com.penseprecifique.api.shared.dto.response.compra.VinculoNotaResponse;
import com.penseprecifique.api.shared.exception.BusinessException;
import com.penseprecifique.api.shared.exception.ResourceNotFoundException;
import com.penseprecifique.api.shared.validation.DocumentoFiscal;
import com.penseprecifique.api.util.IdentificadorFormatter;
import com.penseprecifique.api.util.PageableOrdenacaoResolver;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** #688/RN-NOVA-16: altera somente memória das próximas leituras, nunca compras anteriores. */
@Service
@RequiredArgsConstructor
@Transactional
public class HistoricoVinculoNotaService {
    private final VinculoItemNotaRepository repository;
    private final InsumoRepository insumoRepository;
    private final ClienteRepository clienteRepository;
    private final CompraService compraService;
    private static final Map<String, String> ORDENACAO = Map.of("nomeItem", "nomeItem", "emitenteNome", "emitenteNome",
            "updatedAt", "updatedAt", "createdAt", "createdAt");

    @Transactional(readOnly = true)
    public Page<VinculoNotaResponse> listar(String busca, UUID fornecedorId, String emitenteCnpj,
                                            UUID insumoId, Boolean ignorar, Pageable pageable) {
        UUID usuario = compraService.getUsuarioAutenticado().getId();
        if (busca != null && busca.length() > 500) throw new BusinessException("A busca aceita até 500 caracteres.");
        String cnpj = DocumentoFiscal.normalizar(emitenteCnpj);
        if (cnpj != null && !DocumentoFiscal.cnpjValido(cnpj)) throw new BusinessException("Informe um CNPJ válido para filtrar os vínculos.");
        String documentoFornecedor = fornecedorId == null ? null : clienteRepository.findByIdAndUsuarioId(fornecedorId, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Fornecedor não encontrado")).getDocumento();
        Pageable ordenado = PageableOrdenacaoResolver.resolver(pageable, ORDENACAO, "nomeItem, emitenteNome, updatedAt, createdAt");
        if (ordenado.getPageSize() > 100) throw new BusinessException("A página aceita até 100 vínculos.");
        Sort sort = ordenado.getSort().isSorted() ? ordenado.getSort() : Sort.by(Sort.Direction.DESC, "updatedAt");
        ordenado = PageRequest.of(ordenado.getPageNumber(), ordenado.getPageSize(), sort.and(Sort.by("id")));
        if (fornecedorId != null && documentoFornecedor == null) return Page.empty(ordenado);
        final String filtroCnpj = cnpj;
        Specification<VinculoItemNota> filtro = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("usuario").get("id"), usuario));
            if (filtroCnpj != null) p.add(cb.equal(root.get("emitenteCnpj"), filtroCnpj));
            if (documentoFornecedor != null) p.add(cb.equal(root.get("emitenteCnpj"), documentoFornecedor));
            if (insumoId != null) p.add(cb.equal(root.get("insumo").get("id"), insumoId));
            if (ignorar != null) p.add(cb.equal(root.get("ignorar"), ignorar));
            if (busca != null && !busca.isBlank()) {
                String texto = padrao(busca.strip().toLowerCase(Locale.ROOT));
                String normalizado = padrao(CasamentoPorNome.normalizarChave(busca));
                p.add(cb.or(cb.like(root.get("nomeItemNormalizado"), normalizado, '\\'),
                        cb.like(cb.lower(root.get("emitenteNome")), texto, '\\'),
                        cb.like(cb.lower(root.join("insumo", JoinType.LEFT).get("nome")), texto, '\\')));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<VinculoItemNota> pagina = repository.findAll(filtro, ordenado);
        Map<String, Cliente> cadastros = clienteRepository.findByUsuarioIdAndDocumentoIn(usuario,
                pagina.getContent().stream().map(VinculoItemNota::getEmitenteCnpj).distinct().toList()).stream()
                .collect(Collectors.toMap(Cliente::getDocumento, Function.identity(), (a, b) -> a));
        return pagina.map(v -> resposta(v, cadastros.get(v.getEmitenteCnpj())));
    }

    public VinculoNotaResponse editar(UUID id, VinculoNotaRequest request) {
        UUID usuario = compraService.getUsuarioAutenticado().getId();
        VinculoItemNota v = buscar(id, usuario); definirDestino(v, request.insumoId(), request.fator(), usuario);
        return salvar(v, usuario);
    }

    public VinculoNotaResponse ignorar(UUID id, IgnorarVinculoNotaRequest request) {
        UUID usuario = compraService.getUsuarioAutenticado().getId();
        VinculoItemNota v = buscar(id, usuario);
        if (request.ignorar() == null) throw new BusinessException("Informe se o item deve ser ignorado.");
        if (request.ignorar()) { v.setIgnorar(true); v.setInsumo(null); v.setFator(null); }
        else definirDestino(v, request.insumoId(), request.fator(), usuario);
        return salvar(v, usuario);
    }

    public void desfazer(UUID id) {
        UUID usuario = compraService.getUsuarioAutenticado().getId(); repository.delete(buscar(id, usuario));
    }

    private VinculoItemNota buscar(UUID id, UUID usuario) {
        return repository.findByIdAndUsuarioId(id, usuario).orElseThrow(() -> new ResourceNotFoundException("Vínculo não encontrado"));
    }

    private void definirDestino(VinculoItemNota v, UUID insumoId, BigDecimal fator, UUID usuario) {
        if (insumoId == null || fator == null || fator.signum() <= 0 || fator.scale() > 4 || fator.precision() - fator.scale() > 11) {
            throw BusinessException.explicado("Destino do vínculo inválido", "Escolha um insumo e um fator maior que zero, com até quatro casas decimais.",
                    "O fator converte a quantidade da nota para a unidade do insumo.", "Revise o insumo e o fator e tente salvar novamente.");
        }
        Insumo insumo = insumoRepository.findByIdAndUsuarioIdAndDeletedAtIsNull(insumoId, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Insumo não encontrado"));
        if (!Boolean.TRUE.equals(insumo.getAtivo())) throw BusinessException.explicado("Insumo inativo",
                "O insumo escolhido está inativo.", "Vínculos futuros precisam de insumo ativo ou em rascunho.", "Reative o insumo ou escolha outro.");
        v.setInsumo(insumo); v.setFator(fator); v.setIgnorar(false);
    }

    private VinculoNotaResponse salvar(VinculoItemNota v, UUID usuario) {
        v.setOrigem(OrigemVinculoItemNota.MANUAL); repository.saveAndFlush(v);
        return resposta(v, clienteRepository.findByUsuarioIdAndDocumento(usuario, v.getEmitenteCnpj()).orElse(null));
    }

    private static VinculoNotaResponse resposta(VinculoItemNota v, Cliente cadastro) {
        Insumo i = v.getInsumo();
        InsumoProposto insumo = i == null ? null : new InsumoProposto(i.getId(), IdentificadorFormatter.formatar("INS", i.getNumero()),
                i.getNome(), i.getMarca(), i.getUnidadeMedida() == null ? null : i.getUnidadeMedida().getSigla(), Boolean.TRUE.equals(i.getRascunho()));
        return new VinculoNotaResponse(v.getId(), v.getNomeItem(), v.getEmitenteCnpj(), v.getEmitenteNome(),
                cadastro == null ? null : cadastro.getId(), cadastro == null ? v.getEmitenteNome() : cadastro.getNome(),
                insumo, v.getFator(), Boolean.TRUE.equals(v.getIgnorar()), v.getOrigem(), v.getCreatedAt(), v.getUpdatedAt());
    }

    private static String padrao(String valor) { return "%" + valor.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"; }
}
