package com.penseprecifique.api.compra;

import com.penseprecifique.api.caixa.*;
import com.penseprecifique.api.orcamento.*;
import com.penseprecifique.api.produto.CustoMaterialService;
import com.penseprecifique.api.shared.domain.entity.*;
import com.penseprecifique.api.shared.domain.enums.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CmvSemCustoTest {
    @Test void linhaLegadaSemReferenciaMarcaSemCustoSemInventarEstimativa() {
        var orc=mock(OrcamentoRepository.class);
        var caixa=mock(VendaCaixaRepository.class);
        var itens=mock(VendaCaixaItemRepository.class);
        var custo=mock(CustoMaterialService.class);
        var custom=mock(VendaCaixaItemCustomizacaoRepository.class);
        var componentes=mock(VendaCaixaItemComponenteRepository.class);
        var service=new CmvService(orc,mock(OrcamentoItemRepository.class),mock(OrcamentoItemCustomizacaoRepository.class),
                mock(OrcamentoItemComponenteRepository.class),caixa,itens,custom,componentes,custo);
        UUID usuario=UUID.randomUUID(), id=UUID.randomUUID();
        LocalDate dia=LocalDate.of(2026,5,7);
        var venda=VendaCaixa.builder().id(id).numero(7).dataVenda(dia.atStartOfDay()).total(new BigDecimal("13.47")).build();
        var item=VendaCaixaItem.builder().id(UUID.randomUUID()).vendaCaixa(venda).quantidade(BigDecimal.ONE).build();
        when(orc.findByUsuarioIdAndStatusAndDeletedAtIsNullAndDataEntregaBetween(any(),any(),any(),any())).thenReturn(List.of());
        when(caixa.findByUsuarioIdAndStatusAndDataVendaBetween(eq(usuario),eq(StatusVendaCaixa.CONCLUIDA),any(),any())).thenReturn(List.of(venda));
        when(itens.findByVendaCaixaIdIn(List.of(id))).thenReturn(List.of(item));
        when(custom.findByVendaCaixaItemIdIn(anyList())).thenReturn(List.of());
        when(componentes.findByVendaCaixaItemIdIn(anyList())).thenReturn(List.of());
        var resposta=service.vendas(usuario,dia,dia).getFirst();
        assertTrue(resposta.semCusto()); assertFalse(resposta.custoEstimado());
        assertEquals(new BigDecimal("0.00"),resposta.cmv()); assertEquals("1 × Item sem referência",resposta.itens());
    }
}
