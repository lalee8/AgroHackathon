package az.aqroinvest.application;

import az.aqroinvest.application.ApplicationDtos.*;
import az.aqroinvest.audit.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Tag(name = "Admin: Müraciətlər", description = "Statusun dəyişdirilməsi, qərar və hesabat ixracı")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/admin/applications")
@RequiredArgsConstructor
public class AdminApplicationController {
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.of("Asia/Baku"));

    private final ApplicationService service;
    private final AuditService audit;

    @Operation(summary = "Statusu dəyiş (sonlu avtomat qaydaları ilə)")
    @PostMapping("/{id}/status")
    public ApplicationView changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest req) {
        return service.changeStatus(id, req);
    }

    @Operation(summary = "Müraciətləri CSV formatında ixrac et (Excel uyğun)")
    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) ApplicationStatus status,
                                         @RequestParam(required = false) Long competitionId) {
        List<ApplicationListItem> items = service.search(status, competitionId, null, null);
        StringBuilder sb = new StringBuilder("﻿");
        sb.append("Nömrə;Status;Qərar;Müraciətçi;Sahə kodu;Sahə;Rayon;İstiqamət;Müsabiqə;İnvestisiya (AZN);İş yerləri;Avto bal;Ekspert balı;Yekun bal;Göndərilib\n");
        for (ApplicationListItem i : items) {
            sb.append(csv(i.appNumber())).append(';').append(csv(i.statusLabel())).append(';')
                    .append(csv(i.decisionLabel())).append(';').append(csv(i.applicantName())).append(';')
                    .append(csv(i.plotCode())).append(';').append(csv(i.plotName())).append(';')
                    .append(csv(i.districtLabel())).append(';').append(csv(i.categoryLabel())).append(';')
                    .append(csv(i.competitionCode())).append(';')
                    .append(i.investmentAmount() == null ? "" : i.investmentAmount().toPlainString()).append(';')
                    .append(i.jobsPlanned() == null ? "" : i.jobsPlanned()).append(';')
                    .append(i.autoScore() == null ? "" : i.autoScore()).append(';')
                    .append(i.expertAverage() == null ? "" : i.expertAverage()).append(';')
                    .append(i.finalScore() == null ? "" : i.finalScore()).append(';')
                    .append(i.submittedAt() == null ? "" : TS.format(i.submittedAt())).append('\n');
        }
        audit.log("REPORT_EXPORTED", "Application", null, items.size() + " sətir");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"muracietler.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String s) {
        if (s == null) return "";
        String v = s.replace("\"", "\"\"");
        // CSV/Formula injection qorunması
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v + "\"";
    }
}
