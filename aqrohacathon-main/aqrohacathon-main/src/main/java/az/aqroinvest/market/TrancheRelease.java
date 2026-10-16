package az.aqroinvest.market;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/** İnvestorun escrow hesabından sahibkara köçürdüyü hissə (mərhələ təsdiqləndikdən sonra). */
@Entity
@Table(name = "tranche_releases")
@Getter
@Setter
@NoArgsConstructor
public class TrancheRelease {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interest_id", nullable = false)
    private Long interestId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "released_by")
    private Long releasedBy;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;
}
