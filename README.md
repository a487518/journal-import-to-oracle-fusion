# Journals data import to Oracle Fusion using Apache Camel.
Apache Camel service to read the file from local directory or SFTP and load Journals data to Oracle Fusion.

### Steps to upload the journals file to Oracle Fusion
- Upload the zip file to UCM
- Submits an ESS interface loader job to load the data to interface tables.
- On success, it submits the journal Import job
- Polls journal import job status until completion

## Build & Run Commands

```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=OracleFusionJournalsImportApplicationTests

# Clean build artifacts
./mvnw clean
```

WSDL-generated Java classes are produced during `generate-sources` phase (via `cxf-codegen-plugin`) and placed in `target/generated-sources/cxf/`. These must not be edited manually.

## Architecture Overview

This is a **file-to-Oracle Fusion Cloud integration** application using Spring Boot + Apache Camel + Apache CXF.

### Integration Flow

1. **File Watcher** (`routes/FileToFusion.java`): Camel route monitors a directory for incoming files
2. **SOAP Client** (`beans/WebServiceClient.java`): Orchestrates the Oracle Fusion import workflow:
   - Uploads file to Oracle UCM (Universal Content Management) via SOAP
   - Submits an ESS interface loader job (`InterfaceLoaderController`)
   - Polls job status every 10 seconds until terminal state
   - On success, submits the journal import job (`JournalImportLauncher`)
   - Polls journal import job status until completion
3. **Archiving**: Processed files move to `Archive/` on success or `Error/` on failure

### Key Components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `WebServiceClient` | `beans/` | All Oracle Fusion SOAP interactions, job orchestration |
| `FileToFusion` | `routes/` | Camel route: file detection → WebServiceClient → archive/error |
| Generated CXF classes | `target/generated-sources/cxf/` → package `org.advika.erpIntegrationService` | SOAP stubs from `ErpIntegrationService.wsdl` |

### Configuration (`application.yaml`)

| Property | Purpose |
|----------|---------|
| `input.journalfile.path` | Local directory to watch for incoming journal files |
| `fusion.wsdlUrl` | Oracle Fusion WSDL endpoint URL |
| `fusion.user` / `fusion.encodedpwd` | Credentials (password is Base64-encoded) |
| `fusion.uploadtoucm.*` | UCM upload parameters (content type, security group, document account) |
| `fusion.importinterfacetable.*` | ESS job definition for the interface loader step |
| `fusion.importmainttables.*` / `fusion.importmaintables.params` | ESS job definition and parameters for the journal import step |

### Logging

Logback rolling file appender writes to `./Logs/log_yyyyMMdd.log` (relative to execution directory), with 30-day retention and 3GB total cap. Log level: INFO.
