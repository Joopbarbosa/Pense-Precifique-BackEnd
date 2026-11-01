package com.penseprecifique.api.testsupport;

import com.penseprecifique.api.auth.UsuarioRepository;
import com.penseprecifique.api.empresa.MetodoPagamentoConfiguravelRepository;
import com.penseprecifique.api.orcamento.OrcamentoService;
import com.penseprecifique.api.shared.domain.enums.StatusOrcamento;
import com.penseprecifique.api.shared.domain.enums.TipoMetodoPagamento;
import com.penseprecifique.api.shared.dto.request.orcamento.AvancaStatusRequest;
import com.penseprecifique.api.shared.dto.request.orcamento.PagamentoOrcamentoRequest;
import com.penseprecifique.api.shared.dto.response.orcamento.OrcamentoDetalheResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Apoio de testes (#1174, RN-NOVA-38): aprovar um orçamento exige o passo Pagamento. Os testes anteriores à regra combinavam o
 * sinal na criação; este apoio monta o passo Pagamento a partir do que o orçamento já combinou (exatamente o mesmo sinal) e
 * de uma forma de pagamento ativa da empresa autenticada, para que continuem exercitando só o que testam.
 */
@Component
public class AprovacaoTeste {

    private static UsuarioRepository usuarios;
    private static MetodoPagamentoConfiguravelRepository metodos;

    AprovacaoTeste(UsuarioRepository u, MetodoPagamentoConfiguravelRepository m) {
        usuarios = u;
        metodos = m;
    }

    /** Igual a {@code avancarStatus}; se o orçamento está ENVIADO e o pedido não traz pagamento, monta o passo Pagamento. */
    public static Object avancar(OrcamentoService servico, UUID id, AvancaStatusRequest pedido) {
        if (pedido.getPagamento() == null) {
            OrcamentoDetalheResponse d = servico.buscarPorId(id);
            if (d.getStatus() == StatusOrcamento.ENVIADO) {
                pedido.setPagamento(pagamentoCombinado(d));
            }
        }
        return servico.avancarStatus(id, pedido);
    }

    public static PagamentoOrcamentoRequest pagamentoCombinado(OrcamentoDetalheResponse d) {
        PagamentoOrcamentoRequest p = new PagamentoOrcamentoRequest();
        p.setMetodoPagamentoId(metodoAtivoDoAutenticado());
        p.setTemSinal(d.isSinalAtivo());
        if (d.isSinalAtivo()) {
            p.setTipoSinal(PagamentoOrcamentoRequest.TipoSinal.VALOR);
            p.setValorSinal(d.getValorSinal());
            p.setSinalAte(d.getSinalAte() != null && !d.getSinalAte().isBefore(LocalDate.now()) ? d.getSinalAte() : LocalDate.now().plusDays(7));
        }
        return p;
    }

    public static UUID metodoAtivoDoAutenticado() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        var usuario = usuarios.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        return MetodosPagamentoTeste.criar(metodos, usuario, TipoMetodoPagamento.PIX, true).getId();
    }
}
