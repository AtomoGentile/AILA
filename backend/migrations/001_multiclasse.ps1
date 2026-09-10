# =============================================================================
# AILA - Migrazione 001 eseguita istruzione per istruzione
# =============================================================================
#
# Perche' esiste questo script invece del semplice
#     wrangler d1 execute circolare_d1 --remote --file=./migrations/001_multiclasse.sql
#
# Quel comando passa dall'endpoint "import" di D1, che NON accetta i token OAuth di
# `wrangler login`: risponde "Authentication error [code: 10000]" anche a un Super
# Administrator. L'endpoint "query", usato da --command, funziona invece con OAuth.
# Questo script manda quindi le stesse identiche istruzioni una per volta con --command.
#
# USO (dalla cartella backend):
#     powershell -ExecutionPolicy Bypass -File .\migrations\001_multiclasse.ps1
#
# Le ALTER TABLE non sono ripetibili: se rilanci lo script una seconda volta danno
# "duplicate column name". E' innocuo e lo script lo dice esplicitamente invece di
# fermarsi, perche' significa solo che quel pezzo era gia' stato applicato.

$ErrorActionPreference = "Continue"
$db = "circolare_d1"

$statements = @(
    'CREATE TABLE IF NOT EXISTS classes ( id TEXT PRIMARY KEY, label TEXT UNIQUE NOT NULL, academic_year TEXT NOT NULL DEFAULT ''2026/2027'', preferences_open BOOLEAN NOT NULL DEFAULT 0, created_at DATETIME DEFAULT CURRENT_TIMESTAMP )',
    'INSERT OR IGNORE INTO classes (id, label, preferences_open) SELECT ''DEFAULT_CLASS'', COALESCE((SELECT class_label FROM app_config WHERE class_id = ''DEFAULT_CLASS''), ''4 CSA''), COALESCE((SELECT preferences_open FROM app_config WHERE class_id = ''DEFAULT_CLASS''), 0)',
    'INSERT OR IGNORE INTO classes (id, label) VALUES (''DEFAULT_CLASS'', ''4 CSA'')',
    'ALTER TABLE users ADD COLUMN class_id TEXT NOT NULL DEFAULT ''DEFAULT_CLASS''',
    'ALTER TABLE calendar_events ADD COLUMN class_id TEXT NOT NULL DEFAULT ''DEFAULT_CLASS''',
    'ALTER TABLE proposals ADD COLUMN class_id TEXT NOT NULL DEFAULT ''DEFAULT_CLASS''',
    'ALTER TABLE interrogation_grids ADD COLUMN class_id TEXT NOT NULL DEFAULT ''DEFAULT_CLASS''',
    'ALTER TABLE seat_map_history ADD COLUMN class_id TEXT NOT NULL DEFAULT ''DEFAULT_CLASS''',
    'UPDATE users SET class_id = ''DEFAULT_CLASS'' WHERE class_id IS NULL OR class_id = ''''',
    'UPDATE calendar_events SET class_id = ''DEFAULT_CLASS'' WHERE class_id IS NULL OR class_id = ''''',
    'UPDATE proposals SET class_id = ''DEFAULT_CLASS'' WHERE class_id IS NULL OR class_id = ''''',
    'UPDATE interrogation_grids SET class_id = ''DEFAULT_CLASS'' WHERE class_id IS NULL OR class_id = ''''',
    'UPDATE seat_map_history SET class_id = ''DEFAULT_CLASS'' WHERE class_id IS NULL OR class_id = ''''',
    'CREATE INDEX IF NOT EXISTS idx_users_class ON users(class_id)',
    'CREATE INDEX IF NOT EXISTS idx_calendar_class ON calendar_events(class_id)',
    'CREATE INDEX IF NOT EXISTS idx_proposals_class ON proposals(class_id)',
    'CREATE INDEX IF NOT EXISTS idx_grids_class ON interrogation_grids(class_id)',
    'CREATE INDEX IF NOT EXISTS idx_seatmap_class ON seat_map_history(class_id)',
    'ALTER TABLE proposals ADD COLUMN edited_at DATETIME',
    'ALTER TABLE interrogation_grids ADD COLUMN closes_at DATETIME',
    'CREATE TABLE IF NOT EXISTS interrogation_submissions ( grid_id TEXT NOT NULL REFERENCES interrogation_grids(id) ON DELETE CASCADE, student_id TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE, submitted_at DATETIME DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY (grid_id, student_id) )'
)

$ok = 0
$skipped = 0
$failed = 0

for ($i = 0; $i -lt $statements.Count; $i++) {
    $sql = $statements[$i]
    $n = $i + 1
    Write-Host ""
    Write-Host "[$n/$($statements.Count)] $($sql.Substring(0, [Math]::Min(80, $sql.Length)))..." -ForegroundColor Cyan

    $output = npx wrangler d1 execute $db --remote --command="$sql" 2>&1 | Out-String

    if ($LASTEXITCODE -eq 0) {
        Write-Host "    OK" -ForegroundColor Green
        $ok++
    } elseif ($output -match "duplicate column name") {
        Write-Host "    Gia' applicata (duplicate column name) - si prosegue" -ForegroundColor Yellow
        $skipped++
    } else {
        Write-Host "    ERRORE:" -ForegroundColor Red
        Write-Host $output
        $failed++
    }
}

Write-Host ""
Write-Host "-----------------------------------------------" 
Write-Host "Eseguite: $ok   Gia' applicate: $skipped   Fallite: $failed"
if ($failed -gt 0) {
    Write-Host "Alcune istruzioni sono fallite: leggi gli errori qui sopra prima di usare l'app." -ForegroundColor Red
} else {
    Write-Host "Migrazione completata." -ForegroundColor Green
    Write-Host "Verifica con:"
    Write-Host "  npx wrangler d1 execute $db --remote --command=`"SELECT id, label FROM classes`""
}
