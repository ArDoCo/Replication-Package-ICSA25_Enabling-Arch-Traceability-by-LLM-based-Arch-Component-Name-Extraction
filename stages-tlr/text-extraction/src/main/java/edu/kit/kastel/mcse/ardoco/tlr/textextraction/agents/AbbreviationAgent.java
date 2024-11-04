/* Licensed under MIT 2023-2024. */
package edu.kit.kastel.mcse.ardoco.tlr.textextraction.agents;

import java.util.List;

import edu.kit.kastel.mcse.ardoco.core.data.DataRepository;
import edu.kit.kastel.mcse.ardoco.core.pipeline.agent.PipelineAgent;
import edu.kit.kastel.mcse.ardoco.tlr.textextraction.informants.AbbreviationInformant;

public class AbbreviationAgent extends PipelineAgent {
    public AbbreviationAgent(DataRepository dataRepository) {
        super(List.of(new AbbreviationInformant(dataRepository)), AbbreviationAgent.class.getSimpleName(), dataRepository);
    }
}
