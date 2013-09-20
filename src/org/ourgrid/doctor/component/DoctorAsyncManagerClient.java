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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.ourgrid.broker.status.TaskStatusInfo;
import org.ourgrid.common.interfaces.control.BrokerControlClient;
import org.ourgrid.common.interfaces.management.BrokerManager;
import org.ourgrid.common.interfaces.status.BrokerStatusProviderClient;
import org.ourgrid.common.interfaces.to.BrokerCompleteStatus;
import org.ourgrid.common.interfaces.to.JobsPackage;

import br.edu.ufcg.lsd.commune.api.FailureNotification;
import br.edu.ufcg.lsd.commune.api.RecoveryNotification;
import br.edu.ufcg.lsd.commune.container.control.ControlOperationResult;
import br.edu.ufcg.lsd.commune.container.servicemanager.client.async.AsyncManagerClient;
import br.edu.ufcg.lsd.commune.container.servicemanager.client.sync.SyncContainerUtil;
import br.edu.ufcg.lsd.commune.identification.ServiceID;

public class DoctorAsyncManagerClient extends AsyncManagerClient<BrokerManager> implements BrokerControlClient, BrokerStatusProviderClient {
	
	private BlockingQueue<Object> addJobBlockingQueue = new ArrayBlockingQueue<Object>(1);

	private DoctorAsyncApplicationClient getDoctorClient() {
		return (DoctorAsyncApplicationClient) getServiceManager().getApplication();
	}
	
	@RecoveryNotification
	public void controlIsUp(BrokerManager control) {
		super.controlIsUp(control);
		getServiceManager().getLog().info("Broker Control is now UP");
		for (final DoctorListener listener : getDoctorClient().getListeners()) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					listener.doctorIsUp();
				}
			}).start();
		}
	}
	
	@FailureNotification
	public void controlIsDown(BrokerManager control) {
		super.controlIsDown(control);
		getServiceManager().getLog().info("Broker Control is now DOWN");
	}

	public void hereIsConfiguration(Map<String, String> arg0) {
		
	}

	public void hereIsUpTime(long arg0) {
		
	}

	public BlockingQueue<Object> getAddJobBlockingQueue() {
		return addJobBlockingQueue;
	}

	public void hereIsCompleteStatus(ServiceID statusProviderServiceID,
			BrokerCompleteStatus status) {
	}

	public void hereIsCompleteJobsStatus(ServiceID statusProviderServiceID,
			JobsPackage jobsStatus) {
		for (DoctorListener listener : getDoctorClient().getListeners()) {
			listener.hereIsJobStatus(jobsStatus);
		}
	}

	public void hereIsJobsStatus(ServiceID statusProviderServiceID,
			JobsPackage jobsStatus) {
	}

	public void hereIsPagedTasks(ServiceID serviceID, Integer jobId,
			Integer offset, List<TaskStatusInfo> pagedTasks) {}

	@Override
	public void operationSucceed(ControlOperationResult arg0) {
		if (arg0.getResult() instanceof Integer) {
			SyncContainerUtil.putResponseObject(addJobBlockingQueue, arg0);
		}
	}
	
}