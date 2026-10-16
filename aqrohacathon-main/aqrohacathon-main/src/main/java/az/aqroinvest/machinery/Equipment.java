package az.aqroinvest.machinery;

import az.aqroinvest.common.StringListConverter;
import az.aqroinvest.plot.District;
import az.aqroinvest.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Texnika elanı: sahibi, növü, rayonu, qiyməti (gün, hektar və ya ay üçün), mövsümü. */
@Entity
@Table(name = "equipment")
@Getter
@Setter
@NoArgsConstructor
public class Equipment {
    public enum PriceUnit { DAY, HA, MONTH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")
    private User owner;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EquipmentType type;

    @Column(nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private District district;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_unit", nullable = false, length = 10)
    private PriceUnit priceUnit;

    @Column(name = "season_from")
    private Integer seasonFrom;

    @Column(name = "season_to")
    private Integer seasonTo;

    /** Uyğun fəaliyyətlər (PlotCategory adları). */
    @Convert(converter = StringListConverter.class)
    @Column(length = 200)
    private List<String> suitable = new ArrayList<>();

    @Column(length = 600)
    private String description;

    @Column(name = "made_year")
    private Integer madeYear;

    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
