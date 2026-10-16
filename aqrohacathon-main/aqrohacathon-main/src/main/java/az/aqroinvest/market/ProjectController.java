package az.aqroinvest.market;

import az.aqroinvest.market.MarketEnums.*;
import az.aqroinvest.market.ProjectDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Tag(name = "Layihələr: sahibkar ↔ investor", description = "Strukturlaşdırılmış layihə təqdimatı, reallıq yoxlaması, investor dili, uyğunlaşdırma və maraq bildirmə")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService service;
    private final TrancheService tranches;
    private final MemoService memos;
    private final java.util.Map<String, java.util.Deque<Long>> memoHits = new java.util.concurrent.ConcurrentHashMap<>();

    @Operation(summary = "Dərc edilmiş layihələr (profil verilərsə uyğunluq balı ilə)")
    @GetMapping("/projects")
    public List<ProjectCard> list(@RequestParam(required = false) ProjectSector sector,
                                  @RequestParam(required = false) DealType deal,
                                  @RequestParam(required = false) List<String> sectors,
                                  @RequestParam(required = false) BigDecimal ticketMin,
                                  @RequestParam(required = false) BigDecimal ticketMax,
                                  @RequestParam(required = false) Risk risk,
                                  @RequestParam(required = false) Integer horizon,
                                  @RequestParam(required = false) String sort) {
        boolean hasProfile = sectors != null || ticketMin != null || ticketMax != null || risk != null || horizon != null;
        return service.list(sector, deal, hasProfile ? new ProfileView(sectors, ticketMin, ticketMax, risk, horizon) : null, sort);
    }

    @Operation(summary = "İstiqamətlər (xanalar): hər birində layihə sayı və ümumi tələb")
    @GetMapping("/projects/sectors")
    public List<Map<String, Object>> sectors() {
        return service.sectors();
    }

    @Operation(summary = "Layihə: təqdimat, reallıq yoxlaması, investor dilində təklif")
    @GetMapping("/projects/{id:\\d+}")
    public ProjectDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @Operation(summary = "Bu sahədə investor axtaran layihələr")
    @GetMapping("/plots/{id}/projects")
    public List<ProjectCard> byPlot(@PathVariable Long id) {
        return service.byPlot(id);
    }

    @Operation(summary = "Reallıq yoxlaması (formada canlı): iddialar istinad göstəriciləri və Monte-Carlo ilə müqayisə olunur")
    @PostMapping("/projects/reality-check")
    public ProjectAnalysisService.Analysis preview(@Valid @RequestBody ProjectRequest r) {
        return service.preview(r);
    }

    @Operation(summary = "Yeni layihə (sahibkar)")
    @PostMapping("/projects")
    public ProjectDetail create(@Valid @RequestBody ProjectRequest r) {
        return service.create(r);
    }

    @Operation(summary = "Layihəni redaktə et (sahibkar)")
    @PutMapping("/projects/{id:\\d+}")
    public ProjectDetail update(@PathVariable Long id, @Valid @RequestBody ProjectRequest r) {
        return service.update(id, r);
    }

    @Operation(summary = "Layihənin statusu: PUBLISHED, FUNDED, CLOSED (sahibkar)")
    @PostMapping("/projects/{id:\\d+}/status")
    public ProjectDetail status(@PathVariable Long id, @RequestParam ProjectStatus status) {
        return service.setStatus(id, status);
    }

    @Operation(summary = "Mənim layihələrim və gələn investor maraqları")
    @GetMapping("/projects/mine")
    public List<Map<String, Object>> mine() {
        return service.mine();
    }

    @Operation(summary = "Layihəyə maraq bildir (investor)")
    @PostMapping("/projects/{id:\\d+}/interest")
    public InterestView interest(@PathVariable Long id, @Valid @RequestBody InterestRequest r) {
        return service.interest(id, r);
    }

    @Operation(summary = "Göndərdiyim maraqlar (qəbul olunanlarda sahibkarın əlaqə məlumatı)")
    @GetMapping("/projects/interests/mine")
    public List<InterestView> myInterests() {
        return service.myInterests();
    }

    @Operation(summary = "Maraqa cavab: görüşə razılıq (əlaqə açılır) və ya imtina (sahibkar)")
    @PostMapping("/projects/interests/{id}/respond")
    public InterestView respond(@PathVariable Long id, @Valid @RequestBody RespondRequest r) {
        return service.respond(id, r);
    }

    @Operation(summary = "İnvestisiya memorandumunun AI icmalı (Əkinçi AI; açar yoxdursa qaydalara əsaslanan mətn)")
    @PostMapping("/projects/{id:\\d+}/memo")
    public MemoService.Memo memo(@PathVariable Long id) {
        String ip = java.util.Objects.requireNonNullElse(az.aqroinvest.audit.AuditService.clientIp(), "?");
        long now = System.currentTimeMillis();
        java.util.Deque<Long> q = memoHits.computeIfAbsent(ip, k -> new java.util.concurrent.ConcurrentLinkedDeque<>());
        while (!q.isEmpty() && q.peekFirst() < now - 60_000) q.pollFirst();
        if (q.size() >= 10) throw new az.aqroinvest.common.ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_REQUESTS", "Çox sayda sorğu. Bir dəqiqə sonra yenidən yoxlayın.");
        q.addLast(now);
        return memos.memo(id);
    }

    @Operation(summary = "Mərhələli maliyyələşmə şablonu: investordan istənilən məbləğ hansı mərhələlərdə və necə təsdiqlənərək ödənilir")
    @GetMapping("/projects/{id:\\d+}/tranches")
    public List<TrancheService.Milestone> trancheTemplate(@PathVariable Long id) {
        return tranches.template(id);
    }

    @Operation(summary = "Razılaşmanın mərhələləri: peyk (NDVI, hava effekti çıxılmaqla), alıcı müqaviləsi və ekspert təsdiqi (investor, sahibkar, admin)")
    @GetMapping("/projects/interests/{id}/tranches")
    public TrancheService.Plan tranchePlan(@PathVariable Long id) {
        return tranches.plan(id);
    }

    @Operation(summary = "Təsdiqlənmiş mərhələnin ödənilməsi: escrow hesabından sahibkara köçürmə (yalnız investor)")
    @PostMapping("/projects/interests/{id}/tranches/{seq}/release")
    public TrancheService.Plan release(@PathVariable Long id, @PathVariable int seq) {
        return tranches.release(id, seq);
    }

    @Operation(summary = "İnvestor profili (istiqamətlər, məbləğ, risk, müddət)")
    @GetMapping("/projects/profile")
    public ProfileView profile() {
        return service.profile();
    }

    @Operation(summary = "İnvestor profilini yadda saxla")
    @PutMapping("/projects/profile")
    public ProfileView saveProfile(@RequestBody ProfileView v) {
        return service.saveProfile(v);
    }

    @Operation(summary = "Layihəni yoxlanılmış kimi işarələ (admin)")
    @PostMapping("/admin/projects/{id}/verify")
    public ProjectDetail verify(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean verified) {
        return service.verify(id, verified);
    }
}
