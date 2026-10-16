package az.aqroinvest.seed;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Demo sənədlər üçün minimal, etibarlı PDF faylı yaradır (xarici kitabxanasız). */
final class SamplePdf {
    private SamplePdf() {}

    static byte[] create(String title, List<String> lines) {
        StringBuilder content = new StringBuilder();
        content.append("BT /F1 18 Tf 60 780 Td (").append(esc(title)).append(") Tj ET\n");
        content.append("0.18 0.49 0.20 RG 2 w 60 765 m 535 765 l S\n");
        int y = 735;
        for (String l : lines) {
            content.append("BT /F1 11 Tf 60 ").append(y).append(" Td (").append(esc(l)).append(") Tj ET\n");
            y -= 18;
        }
        content.append("BT /F1 8 Tf 60 40 Td (Akinchi 2.0 - demo senedi / sample document) Tj ET\n");
        String stream = content.toString();

        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        objects.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>");
        objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        objects.add("<< /Length " + stream.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + stream + "endstream");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
        }
        int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int off : offsets) pdf.append(String.format("%010d 00000 n \n", off));
        pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Helvetica (WinAnsi) Azərbaycan hərflərini dəstəkləmədiyi üçün transliterasiya. */
    private static String esc(String s) {
        String t = s.replace("ə", "e").replace("Ə", "E").replace("ı", "i").replace("İ", "I")
                .replace("ğ", "g").replace("Ğ", "G").replace("ş", "s").replace("Ş", "S")
                .replace("ç", "c").replace("Ç", "C").replace("ö", "o").replace("Ö", "O")
                .replace("ü", "u").replace("Ü", "U");
        t = t.replaceAll("[^\\x20-\\x7E]", "?");
        return t.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
