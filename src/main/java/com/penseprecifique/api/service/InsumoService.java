package com.penseprecifique.api.service;

import com.penseprecifique.api.dto.request.BaixaManualInsumoRequestDTO;
import com.penseprecifique.api.dto.request.InsumoRequestDTO;
import com.penseprecifique.api.dto.response.InsumoResponseDTO;
import com.penseprecifique.api.dto.response.MovimentacaoInsumoResponseDTO;
import com.penseprecifique.api.dto.response.ProdutoRelacionadoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface InsumoService {

    Page<InsumoResponseDTO> listar(Pageable pageable);

    InsumoResponseDTO buscarPorId(UUID id);

    InsumoResponseDTO cadastrar(InsumoRequestDTO request);

    InsumoResponseDTO editar(UUID id, InsumoRequestDTO request);

    void inativar(UUID id);

    MovimentacaoInsumoResponseDTO baixaManual(UUID insumoId, BaixaManualInsumoRequestDTO request);

    Page<MovimentacaoInsumoResponseDTO> listarMovimentacoes(UUID insumoId, Pageable pageable);

    List<ProdutoRelacionadoResponse> listarProdutosRelacionados(UUID insumoId);
}
