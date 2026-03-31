package com.rag.adapter.inbound.rest;

import com.rag.adapter.inbound.dto.request.CreateSpaceRequest;
import com.rag.adapter.inbound.dto.request.UpdateAccessRulesRequest;
import com.rag.adapter.inbound.dto.response.SpaceResponse;
import com.rag.application.identity.SpaceApplicationService;
import com.rag.domain.identity.model.AccessRule;
import com.rag.domain.identity.model.TargetType;
import com.rag.domain.shared.model.SecurityLevel;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spaces")
public class SpaceController {

    private final SpaceApplicationService spaceService;

    public SpaceController(SpaceApplicationService spaceService) {
        this.spaceService = spaceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceResponse createSpace(@Valid @RequestBody CreateSpaceRequest req) {
        var space = spaceService.createSpace(
            req.name(), req.description(), req.ownerTeam(), req.language(), req.indexName());
        return SpaceResponse.from(space);
    }

    @GetMapping
    public List<SpaceResponse> listSpaces(@RequestHeader("X-User-Id") UUID userId) {
        return spaceService.listAccessibleSpaces(userId).stream()
            .map(SpaceResponse::from).toList();
    }

    @GetMapping("/{spaceId}")
    public SpaceResponse getSpace(@PathVariable UUID spaceId) {
        return SpaceResponse.from(spaceService.getSpace(spaceId));
    }

    @PutMapping("/{spaceId}/access-rules")
    public SpaceResponse updateAccessRules(@PathVariable UUID spaceId,
                                            @Valid @RequestBody UpdateAccessRulesRequest req) {
        List<AccessRule> rules = req.rules().stream().map(r ->
            new AccessRule(null, spaceId,
                TargetType.valueOf(r.targetType()), r.targetValue(),
                r.docSecurityClearance() != null
                    ? SecurityLevel.valueOf(r.docSecurityClearance())
                    : SecurityLevel.ALL)
        ).toList();
        return SpaceResponse.from(spaceService.updateAccessRules(spaceId, rules));
    }
}
