package az.aqroinvest.buyer;

import az.aqroinvest.plot.PlotCategory;

public final class BuyerEnums {
    private BuyerEnums() {}

    public enum BuyerType {
        EMAL("Emal müəssisəsi"), IXRAC("İxracatçı"), PERAKENDE("Pərakəndə şəbəkə"), HORECA("Restoran və otel");
        private final String label;
        BuyerType(String l) { label = l; }
        public String label() { return label; }
    }

    /** Məhsullar və onların kənd təsərrüfatı kateqoriyası (sahə ilə uyğunlaşdırma üçün). */
    public enum Product {
        BUGDA("Buğda", PlotCategory.EKINCILIK), ARPA("Arpa", PlotCategory.EKINCILIK), QARGIDALI("Qarğıdalı", PlotCategory.EKINCILIK),
        NAR("Nar", PlotCategory.BAGCILIQ), ALMA("Alma", PlotCategory.BAGCILIQ), FINDIQ("Fındıq", PlotCategory.BAGCILIQ),
        UZUM_TEXNIKI("Texniki üzüm", PlotCategory.UZUMCULUK), UZUM_SUFRE("Süfrə üzümü", PlotCategory.UZUMCULUK),
        POMIDOR("Pomidor", PlotCategory.ISTIXANA), XIYAR("Xiyar", PlotCategory.ISTIXANA),
        SUD("Çiy süd", PlotCategory.HEYVANDARLIQ), ET("Ət", PlotCategory.HEYVANDARLIQ), BAL("Bal", PlotCategory.ARICILIQ);
        private final String label;
        private final PlotCategory category;
        Product(String l, PlotCategory c) { label = l; category = c; }
        public String label() { return label; }
        public PlotCategory category() { return category; }
    }

    public enum DemandStatus {
        PENDING("Yoxlanılır"), VERIFIED("Təsdiqlənmiş tələb"), REJECTED("Rədd edilib"), CLOSED("Bağlanıb");
        private final String label;
        DemandStatus(String l) { label = l; }
        public String label() { return label; }
    }

    public enum LoiStatus {
        REQUESTED("Alıcının cavabı gözlənilir"), SIGNED("İmzalanıb"), DECLINED("Rədd edilib");
        private final String label;
        LoiStatus(String l) { label = l; }
        public String label() { return label; }
    }
}
