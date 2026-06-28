package com.penseprecifique.api.service;

import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component("pdfHelper")
public class PdfHelper {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String moeda(BigDecimal valor) {
        if (valor == null) return "R$ 0,00";
        return String.format("R$ %,.2f", valor);
    }

    public String hoje() {
        return LocalDate.now().format(FMT_DATA);
    }

    public BigDecimal calcularMulta(BigDecimal total, BigDecimal percentualMulta) {
        if (total == null || percentualMulta == null) return BigDecimal.ZERO;
        return total.multiply(percentualMulta).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }
}
