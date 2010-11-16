package org.mycore.frontend.editor.validation;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

import org.mycore.common.MCRException;

public class MCRDecimalValidator extends MCRValidatorBase {

    @Override
    public boolean hasRequiredProperties() {
        return "decimal".equals(getProperty("type"));
    }

    @Override
    protected boolean isValidOrDie(String input) throws Exception {
        string2double(input);
        return true;
    }

    protected double string2double(String string) throws Exception {
        Locale locale = getLocale();
        NumberFormat nf = NumberFormat.getNumberInstance(locale);

        string = string.trim();
        checkForIllegalCharacters(string);

        if (nf instanceof DecimalFormat)
            checkForMultipleDecimalSeparators(string, (DecimalFormat) nf);

        return nf.parse(string).doubleValue();
    }

    private void checkForMultipleDecimalSeparators(String string, DecimalFormat df) {
        DecimalFormatSymbols dfs = df.getDecimalFormatSymbols();
        String patternNonDecimalSeparators = "[^" + dfs.getDecimalSeparator() + "]";
        String decimalSeparatorsLeftOver = string.replaceAll(patternNonDecimalSeparators, "");

        if (decimalSeparatorsLeftOver.length() > 1) {
            throw new MCRException("Number contains repeated decimal separator: " + string);
        }

    }

    private void checkForIllegalCharacters(String string) {
        if (!string.matches("[0-9,.]+"))
            throw new MCRException("Number contains illegal characters: " + string);
    }

    private Locale getLocale() {
        String format = getProperty("format");
        if (format == null)
            return Locale.getDefault();
        else
            return new Locale(format);
    }
}
