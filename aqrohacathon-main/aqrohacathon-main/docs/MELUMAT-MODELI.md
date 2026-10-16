# Məlumat modeli

Texniki tapşırıqda tələb olunan dörd əsas obyekt **sahibkar, təsərrüfat sahəsi, müsabiqə və müraciət** strukturlaşdırılıb. Ətrafında tarixçə, sənədlər, qiymətləndirmə, bildiriş, audit və inteqrasiya (outbox) cədvəlləri qurulub.

Sxem Flyway ilə versiyalanır: [`V1__init_schema.sql`](../src/main/resources/db/migration/V1__init_schema.sql) (əsas sxem), [`V2__telegram.sql`](../src/main/resources/db/migration/V2__telegram.sql), [`V3__land_designation.sql`](../src/main/resources/db/migration/V3__land_designation.sql) (torpaq təyinatı), [`V4__marketplace.sql`](../src/main/resources/db/migration/V4__marketplace.sql) (sahibkar ↔ investor bazarı, alıcılar). SQL həm **PostgreSQL 16**, həm də **H2 (PostgreSQL rejimi)** üzərində eyni işləyir.

## ER diaqramı

```mermaid
erDiagram
    USERS ||--o{ APPLICATIONS : "müraciət edir"
    USERS ||--o{ COMPETITIONS : "yaradır / təsdiqləyir"
    USERS ||--o{ EVALUATIONS : "qiymətləndirir (ekspert)"
    USERS ||--o{ NOTIFICATIONS : "alır"
    COMPETITIONS ||--o{ COMPETITION_PLOTS : ""
    PLOTS ||--o{ COMPETITION_PLOTS : ""
    COMPETITIONS ||--o{ APPLICATIONS : ""
    PLOTS ||--o{ APPLICATIONS : ""
    APPLICATIONS ||--o{ APPLICATION_STATUS_HISTORY : "tarixçə"
    APPLICATIONS ||--o{ APPLICATION_DOCUMENTS : "sənədlər"
    APPLICATIONS ||--o{ EVALUATIONS : ""

    USERS {
        bigint id PK
        varchar email UK
        varchar password_hash "BCrypt(12)"
        varchar full_name
        varchar phone
        varchar fin "FİN kod"
        varchar role "INVESTOR | EXPERT | ADMIN"
        boolean enabled
        int failed_logins
        timestamp locked_until
    }
    PLOTS {
        bigint id PK
        varchar code UK "AGD-001"
        varchar name
        varchar district "AGDAM ... KELBECER"
        varchar category "EKINCILIK ... ARICILIQ"
        decimal area_ha
        double latitude
        double longitude
        text polygon "GeoJSON koordinatları"
        int soil_quality "bonitet 0-100"
        boolean irrigation
        boolean electricity
        boolean gas
        decimal road_distance_km
        varchar demining_status "CLEARED | SURVEYED | IN_PROGRESS"
        varchar demining_certificate "ANAMA-..."
        int lease_years
        decimal annual_rent_per_ha
        decimal min_investment
        int min_jobs
        varchar status "AVAILABLE | IN_COMPETITION | AWARDED"
        bigint version "optimistic lock"
    }
    COMPETITIONS {
        bigint id PK
        varchar code UK "MSB-2026-01"
        varchar title
        timestamp start_at
        timestamp deadline_at
        varchar status "DRAFT | PENDING_APPROVAL | PUBLISHED | CLOSED"
        text criteria "JSON: meyarlar və çəkilər"
        bigint created_by FK
        bigint approved_by FK
        text review_note
    }
    APPLICATIONS {
        bigint id PK
        varchar app_number UK "AQR-2026-000001"
        varchar tracking_code "8 simvol"
        bigint applicant_id FK
        bigint competition_id FK
        bigint plot_id FK
        varchar applicant_type "INDIVIDUAL | LEGAL"
        varchar company_name
        varchar voen
        varchar fin
        decimal investment_amount
        int own_funds_percent
        int jobs_planned
        int implementation_months
        text technologies
        text business_plan
        boolean idp_status "məcburi köçkün"
        boolean consent_given
        varchar status
        varchar decision "APPROVED | REJECTED"
        int auto_score
        text score_breakdown "JSON izah"
        varchar ektis_farmer_status
        varchar ektis_reference
        bigint version
    }
    APPLICATION_STATUS_HISTORY {
        bigint id PK
        bigint application_id FK
        varchar from_status
        varchar to_status
        text comment
        varchar actor_name
        timestamp created_at
    }
    APPLICATION_DOCUMENTS {
        bigint id PK
        bigint application_id FK
        varchar doc_type
        varchar original_name
        varchar stored_name "UUID"
        varchar content_type "magic bytes ilə"
        bigint size_bytes
        varchar sha256
    }
    EVALUATIONS {
        bigint id PK
        bigint application_id FK
        bigint expert_id FK
        text scores "JSON 0-10"
        int total
    }
    NOTIFICATIONS {
        bigint id PK
        bigint user_id FK
        varchar type
        varchar title
        boolean is_read
    }
    AUDIT_LOGS {
        bigint id PK
        varchar actor_email
        varchar action
        varchar entity_type
        varchar entity_id
        varchar ip_address
        timestamp created_at
    }
    INTEGRATION_EVENTS {
        bigint id PK
        varchar event_id UK "idempotentlik açarı"
        varchar event_type
        varchar aggregate_type
        varchar aggregate_id
        text payload "JSON zərf"
        varchar status "PENDING | SENT | FAILED | DEAD"
        int attempts
        timestamp next_attempt_at
        varchar external_ref "EKTİS istinadı"
    }
```

## Bazar modulu: sahibkar, investor, alıcı

```mermaid
erDiagram
    USERS ||--o{ PROJECTS : "sahibkar kimi təqdim edir"
    PLOTS |o--o{ PROJECTS : "layihənin torpağı"
    PROJECTS ||--o{ PROJECT_INTERESTS : "investor marağı"
    USERS ||--o{ PROJECT_INTERESTS : "investor kimi"
    USERS ||--o| INVESTOR_PROFILES : "uyğunlaşdırma profili"
    USERS ||--o{ BUYER_DEMANDS : "alıcı kimi"
    BUYER_DEMANDS ||--o{ LETTERS_OF_INTENT : "niyyət məktubu"
    APPLICATIONS |o--o{ LETTERS_OF_INTENT : "müraciətə əlavə"
    PROJECTS |o--o{ LETTERS_OF_INTENT : "layihəyə əlavə"
    PROJECT_INTERESTS ||--o{ TRANCHE_RELEASES : "escrow ödənişləri"

    PROJECTS {
        varchar code UK "LYH-2026-001"
        varchar sector "10 istiqamət"
        varchar stage "IDEA, PLAN, LAND, OPERATING"
        decimal scale "ha, MWp, otaq, ton"
        decimal capex
        decimal own_funds
        decimal ask_amount "investordan"
        varchar deal_type "EQUITY, LOAN, REVENUE_SHARE"
        decimal claimed_revenue "sahibkarın iddiası"
        decimal claimed_yield
        boolean verified "Nazirlik yoxlayıb"
    }
    PROJECT_INTERESTS {
        decimal amount
        varchar status "NEW, ACCEPTED, DECLINED"
        timestamp accepted_at "mərhələli maliyyələşmənin başlanğıcı"
    }
    TRANCHE_RELEASES {
        int seq "mərhələ nömrəsi"
        decimal amount
        text evidence "peyk və ya alıcı sübutu"
        bigint released_by "investor"
        timestamp released_at
    }
    BUYER_DEMANDS {
        varchar product "BUGDA, NAR, POMIDOR..."
        decimal volume_tons
        decimal price_min
        decimal price_max
        int contract_years
        int radius_km
        varchar status "PENDING, VERIFIED, REJECTED"
    }
    LETTERS_OF_INTENT {
        decimal volume_tons
        decimal price
        varchar status "REQUESTED, SIGNED, DECLINED"
    }
```

- **Əlaqə məlumatlarının məxfiliyi:** `project_interests` statusu `ACCEPTED` olana qədər nə investor sahibkarın, nə sahibkar investorun telefon və e-poçtunu görür.
- **Reallıq yoxlaması saxlanılmır, hər dəfə hesablanır:** iddialar (`claimed_*`) dəyişdikcə və ya istinad göstəriciləri yeniləndikcə nəticə avtomatik dəyişir (layihənin versiyası üzrə keşlənir).
- **Təsdiqlənmiş tələb** (`VERIFIED`) zəmanət deyil: alıcının real ehtiyacının Nazirlik tərəfindən yoxlandığını göstərir.
- **Mərhələlərin statusu saxlanılmır, hər dəfə real məlumatdan hesablanır** (`V5__tranches.sql`): peyk mərhələləri Landsat NDVI seriyasından (sahə və eyni zonadakı nəzarət sahələri), alıcı mərhələsi imzalanmış niyyət məktubundan. Bazada yalnız investorun etdiyi ödənişlər (`tranche_releases`) saxlanılır, `(interest_id, seq)` unikaldır: eyni hissə iki dəfə ödənilə bilməz.
- **Portfel və Böyük Qayıdış paneli bazaya yazmır:** sahələrdən, layihələrdən və alıcı tələblərindən hesablanır, portfel namizədlərinin simulyasiyası sahələrin versiyası üzrə yaddaşda keşlənir.

## Klassifikatorlar

Bütün klassifikatorlar kod və ad cütü şəklində `GET /api/v1/reference` ilə verilir. EKTİS-in rəsmi kodları ilə uyğunlaşdırma (mapping) cədvəli bu ünvan əsasında qurulur.

| Klassifikator | Dəyərlər |
|---|---|
| Rayon (`District`) | AGDAM, FUZULI, XOCAVEND, XOCALI, SUSA, TERTER (Qarabağ); CEBRAYIL, ZENGILAN, QUBADLI, LACIN, KELBECER (Şərqi Zəngəzur) |
| Fəaliyyət istiqaməti (`PlotCategory`) | EKINCILIK, HEYVANDARLIQ, BAGCILIQ, UZUMCULUK, ISTIXANA, ARICILIQ |
| Torpağın hüquqi təyinatı (`LandDesignation`) | EKIN (əkin yeri), COXILLIK (çoxillik əkmələr), OTLAQ (otlaq və biçənək), YARARSIZ (əkinə yararsız: şoran, daşlı, eroziyalı), SENAYE (sənaye və digər) |
| İstifadə ssenarisi (`UseCase`) | 6 kənd təsərrüfatı istiqaməti + GUNES (günəş stansiyası), AQROTURIZM, SOYUDUCU (anbar və emal) |
| Layihə istiqaməti (`ProjectSector`) | yuxarıdakılar + ENERJI, TURIZM, EMAL, AQROTEXNOLOGIYA |
| Rol | INVESTOR (investor / sahibkar), BUYER (alıcı), EXPERT, ADMIN |
| Minatəmizləmə | CLEARED, SURVEYED, IN_PROGRESS |
| Sahənin statusu | AVAILABLE, IN_COMPETITION, AWARDED |
| Müsabiqənin statusu | DRAFT, PENDING_APPROVAL, PUBLISHED, CLOSED |
| Müraciətin statusu | DRAFT, SUBMITTED (*Qəbul edilib*), UNDER_REVIEW (*Baxılır*), INFO_REQUESTED (*Əlavə məlumat tələb olunur*), DECIDED (*Qərar verilib*), WITHDRAWN |
| Qərar | APPROVED, REJECTED |
| Sənəd növü | BUSINESS_PLAN\*, IDENTITY\*, TAX_CERTIFICATE, BANK_REFERENCE, EXPERIENCE_PROOF, ADDITIONAL (\* məcburi) |
| Texnologiyalar | DRIP_IRRIGATION, SOLAR, IOT_SENSORS, DRONES, COLD_STORAGE, ORGANIC, PROCESSING, CLIMATE_CONTROL, PRECISION_AG, WATER_RECYCLING |

## Sahənin həyat dövrü

```mermaid
stateDiagram-v2
    [*] --> AVAILABLE: admin əlavə edir
    AVAILABLE --> IN_COMPETITION: müsabiqə dərc edilir
    IN_COMPETITION --> AWARDED: müraciət APPROVED
    IN_COMPETITION --> AVAILABLE: müsabiqə bağlanır, qalib yoxdur
```

## Müsabiqənin həyat dövrü

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> PENDING_APPROVAL: təsdiqə göndər
    PENDING_APPROVAL --> PUBLISHED: başqa admin təsdiqləyir
    PENDING_APPROVAL --> DRAFT: qeyd ilə geri qaytarılır
    PUBLISHED --> CLOSED: bağlanır
```

## Bütövlük qaydaları

- Bir investorun eyni müsabiqədə eyni sahə üçün yalnız bir aktiv müraciəti ola bilər (`DUPLICATE_APPLICATION`).
- Bir sahə üzrə yalnız bir müraciət `APPROVED` ola bilər (`PLOT_ALREADY_AWARDED`).
- Müraciəti olan və ya müsabiqəyə daxil edilmiş sahə silinə bilməz.
- Meyar çəkilərinin cəmi 100 olmalıdır (`CRITERIA_WEIGHT`).
- Müraciət nömrələri DB sequence (`application_number_seq`) ilə yaradılır, yarış şəraitində də unikal qalır.
- `plots` və `applications` cədvəlləri `version` sütunu ilə optimistic locking istifadə edir.

## İndekslər

`plots(district)`, `plots(category)`, `plots(status)`, `competitions(status)`, `applications(applicant_id)`, `applications(competition_id)`, `applications(status)`, `application_status_history(application_id)`, `notifications(user_id, is_read)`, `audit_logs(created_at)`, `integration_events(status, next_attempt_at)`.
