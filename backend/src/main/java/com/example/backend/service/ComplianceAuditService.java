package com.example.backend.service;

import com.example.backend.model.TelemetryLog;
import com.example.backend.repository.TelemetryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ComplianceAuditService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final TelemetryRepository telemetryRepository;

    public ComplianceAuditService(ChatClient.Builder chatClientBuilder,
                                  VectorStore vectorStore,
                                  TelemetryRepository telemetryRepository) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        this.telemetryRepository = telemetryRepository;
    }

    // Phase 2: Index environmental regulation text into pgvector
    public void indexRegulation(String legalRule, String ruleId) {
        Document doc = new Document(legalRule);
        doc.getMetadata().put("ruleId", ruleId);
        vectorStore.add(List.of(doc));
    }

    // Phase 4: Retrieve relevant rules + sensor logs and perform AI audit via local Ollama
    public String runAudit(String question, String sensorId) {
        // 1. Fetch relevant rules from pgvector
        List<Document> matchingDocs = vectorStore.similaritySearch(question);
        String legalContext = matchingDocs.stream()
                .map(Document::getContent)
                .collect(Collectors.joining("\n---\n"));

        // 2. Fetch telemetry logs from PostgreSQL
        List<TelemetryLog> logs = telemetryRepository.findTop10BySensorIdOrderByTimestampDesc(sensorId);

        // Formatted with %s instead of %.1f to safely handle nulls or string characters like "n"
        String sensorData = logs.stream()
                .map(l -> String.format("Time: %s | SO2: %s | NOx: %s | CO: %s",
                        l.getTimestamp(), l.getSo2Level(), l.getNoxLevel(), l.getCoLevel()))
                .collect(Collectors.joining("\n"));

        // 3. Prompt Construction & RAG Generation
        String promptText = String.format("""
            You are an environmental compliance auditor analyzing industrial sensor telemetry logs against legal regulations.
            
            CRITICAL COMPLIANCE EVALUATION RULES:
            1. If a telemetry value is reported as "n", "n/a", "null", or missing:
               - State clearly that the data for this parameter is "Not Available" or "Missing".
               - DO NOT assume or evaluate a missing reading as compliant or non-compliant on its own unless the regulation explicitly mandates continuous reporting.
               - Do NOT treat "n" or "null" as a numerical value.
            2. For valid numerical readings:
               - Compare the reading directly against the threshold defined in the retrieved regulation document context.
               - Clearly mark each parameter as "Compliant" or "Non-Compliant".
            3. State the overall compliance status clearly based strictly on available data.
            
            LEGAL REGULATION CONTEXT:
            %s
            
            RECENT SENSOR TELEMETRY LOGS (%s):
            %s
            
            AUDITOR QUESTION: %s
            """, legalContext, sensorId, sensorData, question);

        return chatClient.prompt(new Prompt(promptText)).call().content();
    }
}