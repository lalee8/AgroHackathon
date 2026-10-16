package az.aqroinvest.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    private String phone;

    /** Şəxsiyyət vəsiqəsinin FİN kodu (7 simvol). */
    private String fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** İnvestor və ya sahibkar (yalnız INVESTOR rolu üçün; mygov ID ilə ilk girişdə boş qalır və sonra seçilir). */
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", length = 20)
    private UserType userType;

    /** Hüquqi status: fiziki şəxs, hüquqi şəxs və ya xarici vətəndaş (müraciət edən tərəflər üçün). */
    @Enumerated(EnumType.STRING)
    @Column(name = "legal_type", length = 20)
    private LegalType legalType;

    /** VÖEN (hüquqi şəxs və ya fərdi sahibkar), 10 rəqəm. */
    @Column(length = 10)
    private String voen;

    @Column(name = "company_name", length = 200)
    private String companyName;

    /** Təşkilati-hüquqi forma: MMC, ASC, QSC, kooperativ və s. */
    @Column(name = "company_form", length = 30)
    private String companyForm;

    /** Hüquqi şəxsin səlahiyyətli nümayəndəsinin vəzifəsi. */
    @Column(name = "rep_position", length = 120)
    private String repPosition;

    /** Xarici vətəndaş üçün: vətəndaşlıq və pasport nömrəsi. */
    @Column(length = 60)
    private String citizenship;

    @Column(name = "passport_no", length = 20)
    private String passportNo;

    /** Yerli sakin: rayonun keçmiş sakini (qayıdan ailə). */
    @Column(name = "local_resident", nullable = false)
    private boolean localResident;

    @Enumerated(EnumType.STRING)
    @Column(name = "home_district", length = 30)
    private az.aqroinvest.plot.District homeDistrict;

    /** Ünsiyyət dili: az, en, ru. */
    @Column(name = "pref_lang", length = 5)
    private String prefLang;

    /** mygov ID identifikatoru (FİN əsasında); bu hesab dövlət identifikasiyası ilə təsdiqlənib. */
    @Column(name = "mygov_sub", length = 64)
    private String mygovSub;

    private boolean enabled = true;

    @Column(name = "failed_logins")
    private int failedLogins;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Telegram bildirişləri üçün əlaqələndirilmiş chat (istəyə bağlı). */
    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(Instant.now());
    }
}
