/*
 * Copyright (C) 2008 Universidade Federal de Campina Grande
 *  
 * This file is part of OurGrid. 
 *
 * OurGrid is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free 
 * Software Foundation, either version 3 of the License, or (at your option) 
 * any later version. 
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details. 
 * 
 * You should have received a copy of the GNU Lesser General Public License 
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 */
package org.ourgrid.doctor.component;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

import org.ourgrid.common.interfaces.management.BrokerManager;
import org.ourgrid.common.specification.job.JobSpecification;

import br.edu.ufcg.lsd.commune.container.control.ControlOperationResult;
import br.edu.ufcg.lsd.commune.container.servicemanager.client.InitializationContext;
import br.edu.ufcg.lsd.commune.container.servicemanager.client.async.AsyncApplicationClient;
import br.edu.ufcg.lsd.commune.container.servicemanager.client.sync.SyncContainerUtil;
import br.edu.ufcg.lsd.commune.context.ModuleContext;
import br.edu.ufcg.lsd.commune.network.xmpp.CommuneNetworkException;
import br.edu.ufcg.lsd.commune.processor.ProcessorStartException;

public class DoctorAsyncApplicationClient extends AsyncApplicationClient<BrokerManager, DoctorAsyncManagerClient> {
	
	private List<DoctorListener> listeners = new LinkedList<DoctorListener>();

	public DoctorAsyncApplicationClient(ModuleContext context) 
		throws CommuneNetworkException, ProcessorStartException {
		super("BROKER_DOCTOR_ASYNC_UI", context);
	}
	
	protected InitializationContext<BrokerManager, DoctorAsyncManagerClient> createInitializationContext() {
		return new DoctorAsyncInitializationContext();
	}
	
	public void addListener(DoctorListener listener) {
		this.listeners.add(listener);
	}
	
	public List<DoctorListener> getListeners() {
		return listeners;
	}
	
 	/*
	 * Adds job described in the job specification
	 */
	public ControlOperationResult addJob(JobSpecification theJob) {
		BlockingQueue<Object> addJobBlockingQueue = getManagerClient().getAddJobBlockingQueue();
		getManager().addJob(getManagerClient(), theJob);
		return SyncContainerUtil.busyWaitForResponseObject(addJobBlockingQueue, ControlOperationResult.class);
	}
	
	public void cancelJob(Integer jobId) {
		getManager().cancelJob(getManagerClient(), jobId);
	}
	
	public void getJobsStatus(List<Integer> jobIds) {
		getManager().getCompleteJobsStatus(getManagerClient(), jobIds);
	}

	public void getPagedTasks(Integer jobId, Integer offset, Integer pageSize) {
		
		if (isServerApplicationUp()) {
			getManager().getPagedTasks(getManagerClient(), jobId, offset, pageSize);
		}
	}
	
}