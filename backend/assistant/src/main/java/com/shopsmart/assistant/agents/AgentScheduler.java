package com.shopsmart.assistant.agents;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Optional nightly runs (shopsmart.agents.scheduled=true). Runs can always be started by hand from the admin screen. */
@Component
@ConditionalOnProperty(name = "shopsmart.agents.scheduled", havingValue = "true")
public class AgentScheduler {
    private final Agents agents;

    public AgentScheduler(Agents agents) { this.agents = agents; }

    @Scheduled(cron = "${shopsmart.agents.inventory-cron:0 0 2 * * *}")
    public void nightlyInventory() { agents.runInventory("SCHEDULED"); }

    @Scheduled(cron = "${shopsmart.agents.fulfilment-cron:0 30 * * * *}")
    public void hourlyFulfilment() { agents.runFulfilment("SCHEDULED"); }
}
