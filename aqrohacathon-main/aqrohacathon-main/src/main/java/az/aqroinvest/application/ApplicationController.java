package az.aqroinvest.application;

import az.aqroinvest.application.ApplicationDtos.*;
import az.aqroinvest.document.DocumentStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Tag(name = "Müraciətlər (investor)", description = "Elektron müraciətin yaradılması, sənədlər, göndərmə və izləmə")
@SecurityRequirement(name = "bearer")
@RestController
@RequestMapping("/api/v1/applications")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService service;
    private final DocumentStorageService storage;

    @Operation(summary = "Mənim müraciətlərim")
    @GetMapping("/my")
    public List<ApplicationListItem> my() {
        return service.my();
    }

    @Operation(summary = "Qaralama müraciət yarat")
    @PostMapping
    public ApplicationView create(@Valid @RequestBody ApplicationRequest req) {
        return service.createDraft(req);
    }

    @Operation(summary = "Müraciət detalları (sahibi və ya əməkdaş)")
    @GetMapping("/{id}")
    public ApplicationView get(@PathVariable Long id) {
        return service.get(id);
    }

    @Operation(summary = "Gözlənilən avtomatik balın canlı proqnozu")
    @GetMapping("/{id}/score-preview")
    public ScoringService.ScoreResult scorePreview(@PathVariable Long id) {
        return service.scorePreview(id);
    }

    @Operation(summary = "Qaralamanı yenilə")
    @PutMapping("/{id}")
    public ApplicationView update(@PathVariable Long id, @Valid @RequestBody ApplicationRequest req) {
        return service.updateDraft(id, req);
    }

    @Operation(summary = "Sənəd yüklə (PDF/PNG/JPG, maks. 10 MB)")
    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApplicationView upload(@PathVariable Long id, @RequestParam DocumentType type,
                                  @RequestParam("file") MultipartFile file) {
        return service.uploadDocument(id, type, file);
    }

    @Operation(summary = "Qaralamadan sənədi sil")
    @DeleteMapping("/{id}/documents/{docId}")
    public ApplicationView deleteDocument(@PathVariable Long id, @PathVariable Long docId) {
        return service.deleteDocument(id, docId);
    }

    @Operation(summary = "Sənədi yüklə (download)")
    @GetMapping("/{id}/documents/{docId}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @PathVariable Long docId) {
        ApplicationDocument d = service.document(id, docId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(d.getOriginalName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-SHA256", d.getSha256())
                .header("X-Content-Type-Options", "nosniff")
                .body(storage.load(d.getStoredName()));
    }

    @Operation(summary = "Müraciəti göndər - nömrə yaradılır, EKTİS-ə ötürülür")
    @PostMapping("/{id}/submit")
    public ApplicationView submit(@PathVariable Long id) {
        return service.submit(id);
    }

    @Operation(summary = "Əlavə məlumat sorğusuna cavab ver")
    @PostMapping("/{id}/respond")
    public ApplicationView respond(@PathVariable Long id, @Valid @RequestBody RespondRequest req) {
        return service.respond(id, req.comment());
    }

    @Operation(summary = "Müraciəti geri çağır (qaralama silinir)")
    @PostMapping("/{id}/withdraw")
    public ResponseEntity<Void> withdraw(@PathVariable Long id) {
        service.withdraw(id);
        return ResponseEntity.noContent().build();
    }
}
