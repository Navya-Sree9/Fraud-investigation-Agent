package com.capstone.fraudcase.tools;

import com.capstone.fraudcase.agent.common.ToolEnvelope;
import com.capstone.fraudcase.config.CapstoneProperties;
import com.capstone.fraudcase.domain.Hashing;
import com.capstone.fraudcase.domain.ToolStatus;
import com.capstone.fraudcase.observability.Redactor;
import com.capstone.fraudcase.persistence.IdGenerator;
import com.capstone.fraudcase.persistence.ToolTraceEntity;
import com.capstone.fraudcase.persistence.ToolTraceRepository;
import com.capstone.fraudcase.tools.ToolResults.PolicyEvidence;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Single choke point for tool execution: allow-list, per-call timeout, structured failure mapping, redacted trace
 * persistence and metrics. Tool failures never propagate exceptions or provider details to callers or models.
 */
@Component
public class ToolGateway {

    private static final Logger log = LoggerFactory.getLogger(ToolGateway.class);
    public static final String ORIGIN = "SYNTHETIC_TRAINING_ADAPTER_V1";

    private final ToolTraceRepository traces;
    private final IdGenerator ids;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final long timeoutMs;
    private final int maxModelCalls;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ToolGateway(ToolTraceRepository traces, IdGenerator ids, ObjectMapper mapper, MeterRegistry meters,
                       CapstoneProperties properties) {
        this.traces = traces;
        this.ids = ids;
        this.mapper = mapper;
        this.meters = meters;
        this.timeoutMs = properties.tools().timeoutMs();
        this.maxModelCalls = properties.tools().maxModelToolCalls();
    }

    public ToolEnvelope execute(RunScope scope, String toolName, String invokedBy, Map<String, ?> arguments, Supplier<?> call) {
        String traceId = ids.traceId();
        OffsetDateTime started = OffsetDateTime.now(ZoneOffset.UTC);
        long t0 = System.nanoTime();
        ToolStatus status;
        Object result = null;
        String message;
        if (!ToolNames.ALLOW_LIST.contains(toolName) || ("MODEL".equals(invokedBy) && !ToolNames.MODEL_CALLABLE.contains(toolName))) {
            status = ToolStatus.OUT_OF_SCOPE;
            message = "Tool is not on the approved allow-list";
        } else if ("MODEL".equals(invokedBy) && scope.nextModelCall() > maxModelCalls) {
            status = ToolStatus.OUT_OF_SCOPE;
            message = "Model tool-call budget exhausted for this investigation";
        } else {
            Future<?> future = executor.submit(call::get);
            try {
                result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                status = ToolStatus.SUCCESS;
                message = summarise(result);
            } catch (TimeoutException e) {
                future.cancel(true);
                status = ToolStatus.TIMEOUT;
                message = "No response within " + timeoutMs + " ms";
            } catch (ExecutionException e) {
                if (e.getCause() instanceof ToolUnavailableException tue) {
                    status = tue.status();
                    message = tue.getMessage();
                } else if (e.getCause() instanceof IllegalArgumentException) {
                    status = ToolStatus.INVALID_ARGUMENT;
                    message = "Invalid argument";
                } else {
                    status = ToolStatus.FAILED;
                    message = "Tool failed";
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.cancel(true);
                status = ToolStatus.FAILED;
                message = "Interrupted";
            }
        }
        long durationMs = Duration.ofNanos(System.nanoTime() - t0).toMillis();
        String argsJson = toJson(new TreeMap<>(arguments));
        traces.save(new ToolTraceEntity(traceId, scope.caseId(), scope.runNumber(), scope.correlationId(), toolName, invokedBy,
                Hashing.sha256(toolName + "|" + argsJson), Redactor.redact(argsJson, 400), status, durationMs,
                Redactor.redact(message, 600), result == null ? null : Redactor.redact(toJson(result), 20_000), started));
        if ("MODEL".equals(invokedBy)) {
            scope.recordModelTrace(traceId);
        }
        Timer.builder("fraud.tool.duration").tag("tool", toolName).tag("status", status.name()).tag("invokedBy", invokedBy)
                .register(meters).record(Duration.ofMillis(durationMs));
        log.info("tool={} invokedBy={} status={} durationMs={} trace={}", toolName, invokedBy, status, durationMs, traceId);
        return new ToolEnvelope(traceId, toolName, invokedBy, status, started, ORIGIN, message, result,
                scope.evidenceFor(toolName), durationMs);
    }

    private String summarise(Object result) {
        return switch (result) {
            case ToolResults.TransactionDetails t -> String.join("; ", t.observations());
            case ToolResults.DeviceSignals d -> String.join("; ", d.observations());
            case ToolResults.LocationComparison l -> l.anomalyLevel() + " anomaly; " + String.join("; ", l.observations());
            case ToolResults.MerchantRiskProfile m -> String.join("; ", m.observations());
            case ToolResults.PriorDisputes p -> String.join("; ", p.observations());
            case ToolResults.TransactionVelocity v -> String.join("; ", v.observations());
            case ToolResults.ScreeningResult s -> String.join("; ", s.observations());
            case ToolResults.CaseSla s -> s.priority() + " for " + s.queue() + ", target " + s.targetAt();
            case PolicyEvidence p -> "%d passage(s) returned, %d above threshold %.2f%s".formatted(p.passages().size(),
                    p.passages().stream().filter(x -> x.aboveThreshold()).count(), p.threshold(), p.abstained() ? " (abstained)" : "");
            case null -> "No result";
            default -> "Completed";
        };
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    public List<String> allowList() {
        return ToolNames.ALLOW_LIST.stream().sorted().toList();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
