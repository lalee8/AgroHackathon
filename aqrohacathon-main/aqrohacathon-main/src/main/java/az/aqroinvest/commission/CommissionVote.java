package az.aqroinvest.commission;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Komissiya üzvünün müraciət üzrə səsi: lehinə və ya əleyhinə. Qaydalara görə səsvermə açıqdır, bitərəf qalmağa yol
 * verilmir (3.5). Səs iclas bağlanana qədər dəyişdirilə bilər.
 */
@Entity
@Table(name = "commission_votes", uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "member_id"}))
@Getter
@Setter
@NoArgsConstructor
public class CommissionVote {
    public enum Vote {
        FOR("Lehinə"), AGAINST("Əleyhinə");

        private final String label;

        Vote(String label) { this.label = label; }

        public String label() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Vote vote;

    @Column(length = 600)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
