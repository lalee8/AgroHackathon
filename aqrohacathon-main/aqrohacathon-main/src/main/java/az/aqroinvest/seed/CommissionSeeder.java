package az.aqroinvest.seed;

import az.aqroinvest.application.Application;
import az.aqroinvest.application.ApplicationRepository;
import az.aqroinvest.application.ApplicationStatus;
import az.aqroinvest.application.Decision;
import az.aqroinvest.commission.*;
import az.aqroinvest.config.AppProperties;
import az.aqroinvest.user.User;
import az.aqroinvest.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Demo komissiya: Qaydalara uyğun 9 üzv (adlar nümunədir). Portal hesabı olan üzvlər (sədr və iki ekspert) səsini özü
 * verir, digərlərinin səsləri nümunə kimi yaradılır. Baxılan müraciətlərdə 5 səs var, kvorum 6: demoda ekspert daxil olub
 * səs verəndə kvorum toplanır və qərar mümkün olur.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class CommissionSeeder implements CommandLineRunner {
    private final AppProperties props;
    private final CommissionMemberRepository members;
    private final CommissionVoteRepository votes;
    private final ApplicationRepository applications;
    private final UserRepository users;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed().enabled() || members.count() > 0 || users.count() == 0) return;
        // Qaydalar 3.1: 9 üzv. Portal hesabı olanlar (sədr və iki üzv) səsini özü verir
        List<CommissionMember> m = new ArrayList<>();
        m.add(member("Leyla Həsənova", "Departament rəhbəri", Institution.KTN, CommissionMember.Role.CHAIR, "nazirlik@aqroinvest.az", 1));
        m.add(member("Dr. Elçin Quliyev", "Aqrar Elm və İnnovasiya Mərkəzi, aqronom", Institution.KTN, CommissionMember.Role.MEMBER, "ekspert@aqroinvest.az", 2));
        m.add(member("Nərmin Əliyeva", "Əmlak Məsələləri Dövlət Xidməti, torpaq kadastrı", Institution.IQT, CommissionMember.Role.MEMBER, "ekspert2@aqroinvest.az", 3));
        m.add(member("Səbuhi Məmmədli", "İnvestisiya siyasəti şöbəsi", Institution.IQT, CommissionMember.Role.MEMBER, null, 4));
        m.add(member("Arzu Hacıyeva", "Dövlət ekoloji ekspertizası", Institution.ETSN, CommissionMember.Role.MEMBER, null, 5));
        m.add(member("Tural Nəsirov", "Yol infrastrukturu şöbəsi", Institution.RINN, CommissionMember.Role.MEMBER, null, 6));
        m.add(member("Kamran Əlizadə", "Ərazi planlaşdırma şöbəsi", Institution.SADK, CommissionMember.Role.MEMBER, null, 7));
        m.add(member("Günay Rəhimova", "Suvarma sistemləri şöbəsi", Institution.SEDA, CommissionMember.Role.MEMBER, null, 8));
        m.add(member("Ramil Əsgərov", "Torpağın yerləşdiyi ərazi üzrə", Institution.XN, CommissionMember.Role.MEMBER, null, 9));
        m.add(member("Rəşad Məmmədov", "Kənd Təsərrüfatı Nazirliyi, müsabiqələr şöbəsi", Institution.KTN, CommissionMember.Role.SECRETARY, "admin@aqroinvest.az", 10));
        members.saveAll(m);

        List<CommissionMember> voting = m.stream().filter(x -> x.getMemberRole().votes()).toList();
        // Hesabı olmayan üzvlərdən biri (xüsusi nümayəndəlik) baxılan müraciətlərdə hələ səs verməyib: kvorum (6) üçün
        // portal üzvünün səsi lazımdır
        List<CommissionMember> early = m.stream().filter(x -> x.getUserId() == null && x.getMemberRole().votes() && x.getInstitution() != Institution.XN).toList();
        int n = 0;
        for (Application a : applications.findAll()) {
            Instant at = a.getDecidedAt() != null ? a.getDecidedAt().minus(1, ChronoUnit.DAYS)
                    : a.getSubmittedAt() == null ? Instant.now() : a.getSubmittedAt().plus(2, ChronoUnit.DAYS);
            if (a.getStatus() == ApplicationStatus.DECIDED) {
                boolean ok = a.getDecision() == Decision.APPROVED;
                for (int i = 0; i < voting.size(); i++) {
                    // Qalib: 8 lehinə, 1 əleyhinə; imtina: 7 əleyhinə, 2 lehinə
                    CommissionVote.Vote v = ok ? (i == voting.size() - 2 ? CommissionVote.Vote.AGAINST : CommissionVote.Vote.FOR)
                            : (i == 3 || i == 6 ? CommissionVote.Vote.FOR : CommissionVote.Vote.AGAINST);
                    String why = v == CommissionVote.Vote.AGAINST ? (ok ? "Su təminatı layihəsi əlavə əsaslandırma tələb edir"
                            : "Yekun bal qalib təklifdən aşağıdır, investisiya layihəsi 4.9 tələblərinə tam uyğun deyil") : null;
                    votes.save(vote(a, voting.get(i), v, why, at.plus(i, ChronoUnit.MINUTES)));
                    n++;
                }
            } else if (a.getStatus() == ApplicationStatus.UNDER_REVIEW) {
                int score = a.getAutoScore() == null ? 50 : a.getAutoScore();
                for (int i = 0; i < early.size(); i++) {
                    boolean yes = score >= 70 || (score >= 55 && i % 2 == 0);
                    CommissionVote.Vote v = yes ? CommissionVote.Vote.FOR : CommissionVote.Vote.AGAINST;
                    String why = yes ? null : "Avtomatik bal aşağıdır: investisiya və iş yeri öhdəlikləri zəifdir";
                    votes.save(vote(a, early.get(i), v, why, at.plus(i, ChronoUnit.HOURS)));
                    n++;
                }
            }
        }
        log.info("Komissiya: {} üzv, {} səs", m.size(), n);
    }

    private CommissionMember member(String name, String position, Institution inst, CommissionMember.Role role, String email, int order) {
        CommissionMember c = new CommissionMember();
        c.setFullName(name);
        c.setPosition(position);
        c.setInstitution(inst);
        c.setMemberRole(role);
        c.setSortOrder(order);
        if (email != null) c.setUserId(users.findByEmailIgnoreCase(email).map(User::getId).orElse(null));
        return c;
    }

    private static CommissionVote vote(Application a, CommissionMember m, CommissionVote.Vote v, String comment, Instant at) {
        CommissionVote cv = new CommissionVote();
        cv.setApplicationId(a.getId());
        cv.setMemberId(m.getId());
        cv.setVote(v);
        cv.setComment(comment);
        cv.setCreatedAt(at);
        return cv;
    }
}
