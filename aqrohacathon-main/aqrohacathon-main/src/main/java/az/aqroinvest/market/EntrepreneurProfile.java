package az.aqroinvest.market;

import az.aqroinvest.plot.District;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sahibkarın qeydiyyat profili: harada, hansı istiqamətdə, torpaq vəziyyəti və nə qədər investisiya lazımdır.
 * Azad edilmiş ərazilərdə torpaq şəxsi mülkiyyətdə deyil: dövlət onu müsabiqə ilə icarəyə verir.
 */
@Entity
@Table(name = "entrepreneur_profiles")
@Getter
@Setter
@NoArgsConstructor
public class EntrepreneurProfile {
    /** LEASED: müsabiqədə qalib olub, dövlətdən icarəyə götürüb; APPLYING: müsabiqəyə müraciət edir; NONE: hələ seçməyib. */
    public enum LandStatus {
        LEASED("Müsabiqə qalibiyəm, torpaq icarədədir"), APPLYING("Müsabiqəyə müraciət edirəm"), NONE("Hələ sahə seçməmişəm");

        private final String label;

        LandStatus(String label) { this.label = label; }

        public String label() { return label; }
    }

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private District district;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ProjectSector sector;

    @Enumerated(EnumType.STRING)
    @Column(name = "land_status", length = 20)
    private LandStatus landStatus;

    @Column(name = "land_ha", precision = 10, scale = 2)
    private BigDecimal landHa;

    @Column(name = "need_amount", precision = 16, scale = 2)
    private BigDecimal needAmount;

    @Column(length = 400)
    private String idea;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
