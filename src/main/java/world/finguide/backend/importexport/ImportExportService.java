package world.finguide.backend.importexport;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ImportExportService {
    public UUID requestExport(UUID planId, ExportFormat format) {
        return UUID.randomUUID();
    }

    public enum ExportFormat {
        JSON,
        CSV,
        XLSX,
        PDF
    }
}
