package az.aqroinvest.application;

public enum DocumentType {
    // Qaydalar 2.4.6 və 4.9: müraciətə əlavə olunan sənədlər
    BUSINESS_PLAN("İnvestisiya layihəsi (Qaydalar 4.9)", true),
    IDENTITY("Şəxsiyyət vəsiqəsi, reyestrdən çıxarış və ya pasport", true),
    TAX_CERTIFICATE("VÖEN və vergi borcunun olmaması haqqında arayış", true),
    BANK_REFERENCE("Maliyyə hesabatı və son 12 ayın bank dövriyyəsi", true),
    EXPERIENCE_PROOF("Təcrübə və idarəetmə potensialı haqqında arayış", true),
    FEE_RECEIPT("İştirak haqqının ödənilməsi (50 manat)", true),
    ADDITIONAL("Əlavə sənəd", false);

    private final String label;
    private final boolean required;

    DocumentType(String label, boolean required) {
        this.label = label;
        this.required = required;
    }

    public String label() { return label; }

    public boolean required() { return required; }
}
