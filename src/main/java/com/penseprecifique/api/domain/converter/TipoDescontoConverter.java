package com.penseprecifique.api.domain.converter;

import com.penseprecifique.api.domain.enums.TipoDesconto;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TipoDescontoConverter implements AttributeConverter<TipoDesconto, String> {

    @Override
    public String convertToDatabaseColumn(TipoDesconto attribute) {
        if (attribute == null) return null;
        return attribute == TipoDesconto.PERCENTUAL ? "%" : "R$";
    }

    @Override
    public TipoDesconto convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return "%".equals(dbData) ? TipoDesconto.PERCENTUAL : TipoDesconto.VALOR;
    }
}
