package com.winter.airesumeoptimizer.module.ai.credential.service;

import com.winter.airesumeoptimizer.infra.ai.AiSelectionSnapshot;
import com.winter.airesumeoptimizer.module.ai.credential.dto.AiCredentialUpsertRequestDTO;
import com.winter.airesumeoptimizer.module.ai.credential.vo.AiCredentialVO;
import java.util.Optional;

public interface AiCredentialService {

    AiCredentialVO get(Long userId);

    AiCredentialVO saveOrReplace(Long userId, AiCredentialUpsertRequestDTO request);

    AiCredentialVO enable(Long userId);

    AiCredentialVO disable(Long userId);

    void delete(Long userId);

    Optional<AiSelectionSnapshot> resolveCurrentSelection(Long userId);

    DecryptedCredentialMaterial resolveMaterial(Long userId, AiSelectionSnapshot selection);

    DecryptedCredentialMaterial candidateMaterial(Long userId, AiCredentialUpsertRequestDTO request);
}
