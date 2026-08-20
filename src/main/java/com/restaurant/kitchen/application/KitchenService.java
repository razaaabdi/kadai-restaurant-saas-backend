package com.restaurant.kitchen.application;

import com.restaurant.kitchen.infrastructure.KotEntity;
import com.restaurant.kitchen.infrastructure.KotRepository;
import com.restaurant.kitchen.infrastructure.KotSeqEntity;
import com.restaurant.kitchen.infrastructure.KotSeqId;
import com.restaurant.kitchen.infrastructure.KotSeqRepository;
import com.restaurant.platform.api.KotStatusChanged;
import com.restaurant.platform.api.RoundConfirmed;
import com.restaurant.platform.api.TenantContext;
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

	public KitchenService(KotRepository kots, KotSeqRepository seqs, ApplicationEventPublisher events) {
		this.kots = kots;
		this.seqs = seqs;
		this.events = events;
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
		KotEntity k = kots.findById(kotId).orElseThrow();
		k.setStatus("PREPARING");
		kots.save(k);
		events.publishEvent(new KotStatusChanged(TenantContext.require().tenantId(), k.getOrderId(), "PREPARING"));
	}

	@Transactional
	public void markReady(UUID kotId) {
		KotEntity k = kots.findById(kotId).orElseThrow();
		k.setStatus("READY");
		kots.save(k);
		events.publishEvent(new KotStatusChanged(TenantContext.require().tenantId(), k.getOrderId(), "READY"));
	}

	public List<KotEntity> byOrder(UUID orderId) {
		return kots.findByOrderId(orderId);
	}
}
