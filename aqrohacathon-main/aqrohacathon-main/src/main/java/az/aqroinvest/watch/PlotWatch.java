package az.aqroinvest.watch;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** İstifadəçinin sevimli (izlədiyi) sahəsi. */
@Entity
@Table(name = "plot_watches")
@Getter
@Setter
@NoArgsConstructor
public class PlotWatch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plot_id", nullable = false)
    private Long plotId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
