package com.capstone.fraudcase.application;

import com.capstone.fraudcase.agent.common.FraudCaseProcessor;
import com.capstone.fraudcase.config.CapstoneProperties;
import com.capstone.fraudcase.domain.ProcessorMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Selects the processor from configuration (capstone.processor-mode) or an explicit per-run override. */
@Component
public class ProcessorRegistry {

    private final Map<ProcessorMode, FraudCaseProcessor> processors = new EnumMap<>(ProcessorMode.class);
    private final ProcessorMode configured;

    public ProcessorRegistry(List<FraudCaseProcessor> all, CapstoneProperties properties) {
        all.forEach(p -> processors.put(p.mode(), p));
        this.configured = ProcessorMode.fromKey(properties.processorMode());
    }

    public ProcessorMode configuredMode() {
        return configured;
    }

    public ProcessorMode resolve(String override) {
        return override == null || override.isBlank() ? configured : ProcessorMode.fromKey(override);
    }

    public FraudCaseProcessor get(ProcessorMode mode) {
        return processors.get(mode);
    }

    public FraudCaseProcessor rules() {
        return processors.get(ProcessorMode.RULES);
    }

    public Map<ProcessorMode, Boolean> availability() {
        Map<ProcessorMode, Boolean> result = new EnumMap<>(ProcessorMode.class);
        processors.forEach((mode, p) -> result.put(mode, p.available()));
        return result;
    }
}
