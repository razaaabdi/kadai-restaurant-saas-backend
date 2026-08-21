package com.restaurant.kitchen.application;

import com.restaurant.kitchen.infrastructure.KotEntity;
import com.restaurant.kitchen.infrastructure.KotRepository;
import com.restaurant.kitchen.infrastructure.KotSeqEntity;
import com.restaurant.kitchen.infrastructure.KotSeqId;
import com.restaurant.kitchen.infrastructure.KotSeqRepository;
import com.restaurant.platform.api.KotStatusChanged;
import com.restaurant.platform.api.RoundConfirmed;
import com.restaurant.platform.api.TenantContext;
import com.restaurant.platform.api.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class KitchenService {
	private final KotRepository kots;
	private final KotSeqRepository seqs;
	private final ApplicationEventPublisher events;
	private final JdbcTemplate jdbc;

	public KitchenService(KotRepository kots, KotSeqRepository seqs, ApplicationEventPublisher events, JdbcTemplate jdbc) {
		this.kots = kots;
		this.seqs = seqs;
		this.events = events;
		this.jdbc = jdbc;
	}

	@Transactional
	public void accept(UUID kotId) {
		KotEntity k = requireKot(kotId);
		if ("ACCEPTED".equals(k.getStatus())) return;
		if (!"NEW".equals(k.getStatus())) throw ApiException.conflict("KOT_TRANSITION", "Only a new KOT can be accepted");
		k.setStatus("ACCEPTED"); kots.save(k);
		events.publishEvent(new KotStatusChanged(TenantContext.require().tenantId(), k.getOrderId(), k.getId(), k.getRoundId(), "ACCEPTED"));
	}

	@EventListener
	@Transactional
	public void onRound(RoundConfirmed ev) {
		KotSeqId sid = new KotSeqId(ev.tenantId(), ev.outletId());
		KotSeqEntity seq = seqs.findById(sid).orElseGet(() -> {
			KotSeqEntity n = new KotSeqEntity();
			n.setTenantId(ev.tenantId());
			n.setOutletId(ev.outletId());
			n.setLastNumber(0);
			return n;
		});
		seq.setLastNumber(seq.getLastNumber() + 1);
		seqs.save(seq);
		KotEntity k = new KotEntity();
		k.setTenantId(ev.tenantId());
		k.setOutletId(ev.outletId());
		k.setOrderId(ev.orderId());
		k.setRoundId(ev.roundId());
		k.setKotNumber(seq.getLastNumber());
		kots.save(k);
	}

	@Transactional
	public void startPrep(UUID kotId) {
		KotEntity k = requireKot(kotId);
		if ("PREPARING".equals(k.getStatus())) return;
		if (!("NEW".equals(k.getStatus()) || "ACCEPTED".equals(k.getStatus()))) throw ApiException.conflict("KOT_TRANSITION", "This KOT cannot start preparation");
		k.setStatus("PREPARING");
		kots.save(k);
		jdbc.update("update order_lines set fulfilment_status='PREPARING', version=version+1 where round_id=? and fulfilment_status in ('SENT_TO_KITCHEN','ACCEPTED')", k.getRoundId());
		events.publishEvent(new KotStatusChanged(TenantContext.require().tenantId(), k.getOrderId(), k.getId(), k.getRoundId(), "PREPARING"));
	}

	@Transactional
	public void markReady(UUID kotId) {
		KotEntity k = requireKot(kotId);
		if ("READY".equals(k.getStatus())) return;
		if (!("PREPARING".equals(k.getStatus()) || "PARTIALLY_READY".equals(k.getStatus()))) throw ApiException.conflict("KOT_TRANSITION", "Only a preparing KOT can be marked ready");
		k.setStatus("READY");
		kots.save(k);
		jdbc.update("update order_lines set fulfilment_status='READY_FOR_PICKUP', version=version+1 where round_id=? and fulfilment_status in ('SENT_TO_KITCHEN','ACCEPTED','PREPARING')", k.getRoundId());
		events.publishEvent(new KotStatusChanged(TenantContext.require().tenantId(), k.getOrderId(), k.getId(), k.getRoundId(), "READY"));
	}

	@Transactional
	public void markItemReady(UUID kotId, UUID itemId) {
		KotEntity k = requireKot(kotId);
		int changed = jdbc.update("update order_lines set fulfilment_status='READY_FOR_PICKUP', version=version+1 where id=? and round_id=? and fulfilment_status='PREPARING'", itemId, k.getRoundId());
		if (changed == 0) throw ApiException.conflict("ITEM_TRANSITION", "Only a preparing item can be marked ready");
		Integer remaining = jdbc.queryForObject("select count(*) from order_lines where round_id=? and fulfilment_status not in ('READY_FOR_PICKUP','PICKED_UP','SERVED','CANCELLED')", Integer.class, k.getRoundId());
		k.setStatus(remaining != null && remaining == 0 ? "READY" : "PARTIALLY_READY"); kots.save(k);
		events.publishEvent(new KotStatusChanged(TenantContext.require().tenantId(), k.getOrderId(), k.getId(), k.getRoundId(), k.getStatus()));
	}

	private KotEntity requireKot(UUID id) { return kots.findById(id).orElseThrow(() -> ApiException.notFound("KOT", "KOT not found")); }

	public List<KotEntity> byOrder(UUID orderId) {
		return kots.findByOrderId(orderId);
	}
}
