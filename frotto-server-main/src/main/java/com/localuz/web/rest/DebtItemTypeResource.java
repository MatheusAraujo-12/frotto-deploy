package com.localuz.web.rest;

import com.localuz.security.AuthoritiesConstants;
import com.localuz.service.DebtItemTypeService;
import com.localuz.service.dto.DebtItemTypeDTO;
import com.localuz.service.dto.DebtItemTypeSaveDTO;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.web.util.HeaderUtil;

@RestController
@RequestMapping("/api")
@Transactional
public class DebtItemTypeResource {

    private final Logger log = LoggerFactory.getLogger(DebtItemTypeResource.class);

    private static final String ENTITY_NAME = "debtItemType";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final DebtItemTypeService debtItemTypeService;

    public DebtItemTypeResource(DebtItemTypeService debtItemTypeService) {
        this.debtItemTypeService = debtItemTypeService;
    }

    @GetMapping("/debt-item-types")
    public List<DebtItemTypeDTO> getDebtItemTypes(@RequestParam(name = "active", required = false, defaultValue = "true") String active) {
        log.debug("REST request to get debt item types with active filter: {}", active);
        return debtItemTypeService.list(active);
    }

    // POST fica aberto a qualquer usuário autenticado (não só ROLE_ADMIN): revisão pré-deploy
    // encontrou uso real em DocumentsPage.tsx (assistente de "Confissão de Dívida" ->
    // createDebtItemTypeInline()), onde qualquer usuário comum cria um novo tipo de dívida
    // inline ao preencher o documento. Restringir POST a ROLE_ADMIN quebrava esse fluxo.
    //
    // Dívida técnica conhecida, não resolvida aqui: DebtItemType é uma tabela global, sem
    // user_id/owner_id/account_id — um tipo criado por um usuário fica visível (GET) e
    // reutilizável por todos os outros usuários do sistema. Isso é uma questão de modelo de
    // dados (avaliar se deveria ser por conta ou continuar global-e-aberto por decisão de
    // produto), não um bug isolado — deixado para uma etapa futura, junto com o trabalho de
    // billing/multiusuário, que provavelmente vai mexer em ownership de qualquer forma.
    @PostMapping("/debt-item-types")
    public ResponseEntity<DebtItemTypeDTO> createDebtItemType(@RequestBody DebtItemTypeSaveDTO payload) throws URISyntaxException {
        log.debug("REST request to create debt item type: {}", payload == null ? null : payload.getName());
        DebtItemTypeDTO result = debtItemTypeService.create(payload);
        return ResponseEntity
            .created(new URI("/api/debt-item-types/" + result.getId()))
            .headers(HeaderUtil.createEntityCreationAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @PatchMapping("/debt-item-types/{id}")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<DebtItemTypeDTO> updateDebtItemType(@PathVariable Long id, @RequestBody DebtItemTypeSaveDTO payload) {
        log.debug("REST request to update debt item type id={} payload={}", id, payload == null ? null : payload.getName());
        DebtItemTypeDTO result = debtItemTypeService.update(id, payload);
        return ResponseEntity
            .ok()
            .headers(HeaderUtil.createEntityUpdateAlert(applicationName, false, ENTITY_NAME, result.getId().toString()))
            .body(result);
    }

    @DeleteMapping("/debt-item-types/{id}")
    @PreAuthorize("hasAuthority(\"" + AuthoritiesConstants.ADMIN + "\")")
    public ResponseEntity<Void> deleteDebtItemType(@PathVariable Long id) {
        log.debug("REST request to deactivate debt item type id={}", id);
        debtItemTypeService.deactivate(id);
        return ResponseEntity
            .noContent()
            .headers(HeaderUtil.createEntityDeletionAlert(applicationName, false, ENTITY_NAME, id.toString()))
            .build();
    }
}
