# Journals data import to Oracle Fusion using Apache Camel.
Apache Camel service to read the file from local directory or SFTP and load Journals data to Oracle Fusion.

### Steps to upload the journals file to Oracel Fusion
- Upload the zip file to UCM
- Submits an ESS interface loader job to load the data to interface tables.
- On success, it submits the jounal Import job
- Polls journal import job status until completion

### Key Components

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `WebServiceClient` | `beans/` | All Oracle Fusion SOAP interactions, job orchestration |
| `FileToFusion` | `routes/` | Camel route: file detection → WebServiceClient → archive/error |
| Generated CXF classes | `target/generated-sources/cxf/` → package `org.advika.erpIntegrationService` | SOAP stubs from `ErpIntegrationService.wsdl` |

### Configuration (`application.properties`)

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
