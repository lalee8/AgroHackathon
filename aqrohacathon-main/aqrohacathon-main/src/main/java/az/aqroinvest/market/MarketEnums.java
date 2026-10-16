package az.aqroinvest.market;

/** Bazar modulunun klassifikatorları. */
public final class MarketEnums {
    private MarketEnums() {}

    public enum Stage {
        IDEA("İdeya"), PLAN("Biznes plan hazırdır"), LAND("Müsabiqədə qalib, torpaq icarədədir"), OPERATING("Fəaliyyətdədir");
        private final String label;
        Stage(String l) { label = l; }
        public String label() { return label; }
    }

    public enum DealType {
        EQUITY("Pay (şirkətdə hissə)"), LOAN("Borc (faizlə)"), REVENUE_SHARE("Gəlirdən pay");
        private final String label;
        DealType(String l) { label = l; }
        public String label() { return label; }
    }

    public enum ProjectStatus {
        DRAFT("Qaralama"), PUBLISHED("Dərc edilib"), FUNDED("Maliyyələşib"), CLOSED("Bağlanıb");
        private final String label;
        ProjectStatus(String l) { label = l; }
        public String label() { return label; }
    }

    public enum InterestStatus {
        NEW("Yeni"), ACCEPTED("Görüşə razılıq verildi"), DECLINED("Rədd edildi");
        private final String label;
        InterestStatus(String l) { label = l; }
        public String label() { return label; }
    }

    public enum Risk {
        LOW("Aşağı risk"), MID("Orta risk"), HIGH("Yüksək risk");
        private final String label;
        Risk(String l) { label = l; }
        public String label() { return label; }
    }
}
