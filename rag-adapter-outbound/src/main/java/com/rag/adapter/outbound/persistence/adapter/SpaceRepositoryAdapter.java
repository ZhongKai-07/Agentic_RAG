package com.rag.adapter.outbound.persistence.adapter;

import com.rag.adapter.outbound.persistence.mapper.SpaceMapper;
import com.rag.adapter.outbound.persistence.repository.AccessRuleJpaRepository;
import com.rag.adapter.outbound.persistence.repository.KnowledgeSpaceJpaRepository;
import com.rag.domain.identity.model.AccessRule;
import com.rag.domain.identity.model.KnowledgeSpace;
import com.rag.domain.identity.port.SpaceRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class SpaceRepositoryAdapter implements SpaceRepository {

    private final KnowledgeSpaceJpaRepository spaceJpa;
    private final AccessRuleJpaRepository ruleJpa;

    public SpaceRepositoryAdapter(KnowledgeSpaceJpaRepository spaceJpa,
                                   AccessRuleJpaRepository ruleJpa) {
        this.spaceJpa = spaceJpa;
        this.ruleJpa = ruleJpa;
    }

    @Override
    public KnowledgeSpace save(KnowledgeSpace space) {
        var saved = spaceJpa.save(SpaceMapper.toEntity(space));
        KnowledgeSpace result = SpaceMapper.toDomain(saved);
        List<AccessRule> rules = ruleJpa.findBySpaceId(space.getSpaceId())
            .stream().map(SpaceMapper::toAccessRuleDomain).toList();
        result.setAccessRules(new java.util.ArrayList<>(rules));
        return result;
    }

    @Override
    public Optional<KnowledgeSpace> findById(UUID spaceId) {
        return spaceJpa.findById(spaceId).map(e -> {
            KnowledgeSpace s = SpaceMapper.toDomain(e);
            List<AccessRule> rules = ruleJpa.findBySpaceId(spaceId)
                .stream().map(SpaceMapper::toAccessRuleDomain).toList();
            s.setAccessRules(new java.util.ArrayList<>(rules));
            return s;
        });
    }

    @Override
    public List<KnowledgeSpace> findAccessibleSpaces(String bu, String team, UUID userId) {
        List<UUID> spaceIds = ruleJpa.findAccessibleSpaceIds(bu, team, userId.toString());
        return spaceJpa.findAllById(spaceIds).stream().map(e -> {
            KnowledgeSpace s = SpaceMapper.toDomain(e);
            List<AccessRule> rules = ruleJpa.findBySpaceId(e.getSpaceId())
                .stream().map(SpaceMapper::toAccessRuleDomain).toList();
            s.setAccessRules(new java.util.ArrayList<>(rules));
            return s;
        }).toList();
    }

    @Override
    public void saveAccessRules(UUID spaceId, List<AccessRule> rules) {
        ruleJpa.saveAll(rules.stream().map(SpaceMapper::toAccessRuleEntity).toList());
    }

    @Override
    @Transactional
    public void deleteAccessRulesBySpaceId(UUID spaceId) {
        ruleJpa.deleteBySpaceId(spaceId);
    }
}
