package az.aqroinvest.market;

import az.aqroinvest.common.StringListConverter;
import az.aqroinvest.market.MarketEnums.Risk;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** İnvestorun maraq profili: istiqamətlər, məbləğ aralığı, risk iştahı, müddət. Uyğunlaşdırma üçün istifadə olunur. */
@Entity
@Table(name = "investor_profiles")
@Getter
@Setter
@NoArgsConstructor
public class InvestorProfile {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Convert(converter = StringListConverter.class)
    @Column(length = 400)
    private List<String> sectors = new ArrayList<>();

    @Column(name = "ticket_min", precision = 16, scale = 2)
    private BigDecimal ticketMin;

    @Column(name = "ticket_max", precision = 16, scale = 2)
    private BigDecimal ticketMax;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Risk risk = Risk.MID;

    @Column(name = "horizon_years")
    private Integer horizonYears;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
