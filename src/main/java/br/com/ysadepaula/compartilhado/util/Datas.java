package br.com.ysadepaula.compartilhado.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/** Formatação de datas em português do Brasil. */
public final class Datas {

    public static final Locale PT_BR = Locale.of("pt", "BR");

    private Datas() {
    }

    /** Ex.: "quinta-feira". */
    public static String diaSemana(LocalDate data) {
        return nomeDia(data.getDayOfWeek());
    }

    public static String nomeDia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, PT_BR);
    }
}
