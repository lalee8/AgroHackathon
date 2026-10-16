package az.aqroinvest.portfolio;

import az.aqroinvest.advisor.LlmGateway;
import az.aqroinvest.audit.AuditService;
import az.aqroinvest.common.ApiException;
import az.aqroinvest.economics.UseCase;
import az.aqroinvest.plot.District;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Tag(name = "Portfel qurucusu", description = "Büdcənin korrelyasiyalı Monte-Carlo ilə diversifikasiya olunmuş portfelə bölünməsi")
@RestController
@RequestMapping("/api/v1/portfolio")
public class PortfolioController {
    static final String EXPLAIN_SYSTEM = """
            Sən "Əkinçi 2.0" portalının investisiya analitikisən. Sənə portfel qurucusunun nəticəsi JSON şəklində verilir.
            İnvestora sadə dildə 6-8 cümlə ilə izah et: büdcə necə bölündü və niyə, risk necə azaldı (diversifikasiya, bir-birini sığortalayan mövqelər),
            ən böyük risk nədir (stress testlər), ayda və ildə nə qədər gəlir gözlənilir, hansı sosial təsir yaranır və növbəti 2 konkret addım.
            Qaydalar: yalnız verilən rəqəmlərə əsaslan, rəqəm uydurma, zəmanət vermə ("orta ssenaridə" de). Azərbaycan dilində yaz.
            Uzun tire (—) işlətmə. Sadə markdown: **qalın** və "- " siyahı. Sonda bir cümlə: rəqəmlər simulyasiyaya əsaslanan təxminlərdir.
            """;

    private final PortfolioService portfolio;
    private final LlmGateway llm;
    private final ObjectMapper mapper;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public PortfolioController(PortfolioService portfolio, LlmGateway llm, ObjectMapper mapper) {
        this.portfolio = portfolio;
        this.llm = llm;
        this.mapper = mapper;
    }

    @Operation(summary = "Büdcə və risk meylinə görə optimal portfel, səmərəli sərhəd, stress testlər və sosial təsir")
    @GetMapping
    public PortfolioService.Result build(@RequestParam(required = false) Long budget, @RequestParam(required = false) Integer risk,
                                         @RequestParam(required = false) Integer horizon, @RequestParam(required = false) List<District> districts,
                                         @RequestParam(required = false) List<UseCase> uses, @RequestParam(required = false) Integer maxShare,
                                         @RequestParam(required = false) Integer maxSector) {
        return portfolio.build(new PortfolioService.Request(budget, risk, horizon, districts, uses, maxShare, maxSector));
    }

    public record ExplainReply(String mode, String text) {}

    @Operation(summary = "Portfelin AI ilə investor dilində izahı (AI açarı yoxdursa qaydalara əsaslanan izah)")
    @PostMapping("/explain")
    public ExplainReply explain(@RequestBody PortfolioService.Request req) {
        limit();
        PortfolioService.Result r = portfolio.build(req);
        String offline = String.join("\n\n", r.summary()) + "\n\n_Rəqəmlər simulyasiyaya əsaslanan təxminlərdir, qərar sizindir._";
        if (!llm.enabled()) return new ExplainReply("offline", offline);
        Map<String, Object> brief = new LinkedHashMap<>();
        brief.put("büdcə", r.budget());
        brief.put("üfüq_il", r.horizon());
        brief.put("risk_meyli_0_ehtiyatlı_100_cəsarətli", r.risk());
        brief.put("yekun", r.totals());
        brief.put("mövqelər", r.positions().stream().map(p -> Map.of("sahə", p.code() + " " + p.plotName(), "rayon", p.district(), "zona", p.zone(),
                "istiqamət", p.useLabel(), "miqyas", p.scale() + " " + p.unit(), "investisiya", p.amount(), "pay_faiz", p.sharePct(),
                "illik_xalis_P50", p.annualNetP50(), "zərər_ehtimalı", p.probLoss(), "url", p.url())).toList());
        brief.put("diversifikasiya", r.diversification());
        brief.put("stress_testlər", r.stress());
        brief.put("sosial_təsir", r.impact());
        brief.put("platformanın_qısa_izahı", r.summary());
        String json;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(brief);
        } catch (Exception e) {
            return new ExplainReply("offline", offline);
        }
        String text = llm.complete(EXPLAIN_SYSTEM, "<portfolio>\n" + json + "\n</portfolio>\nBu portfeli mənə izah et.");
        return text == null ? new ExplainReply("offline", offline) : new ExplainReply("ai", text);
    }

    private void limit() {
        String ip = Objects.requireNonNullElse(AuditService.clientIp(), "?");
        long now = System.currentTimeMillis();
        Deque<Long> q = hits.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        while (!q.isEmpty() && q.peekFirst() < now - 60_000) q.pollFirst();
        if (q.size() >= 10) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Çox sayda sorğu. Bir dəqiqə sonra yenidən yoxlayın.");
        q.addLast(now);
    }
}
