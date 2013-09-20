package org.ourgrid.doctor.component;

import org.ourgrid.common.interfaces.to.JobsPackage;

public interface DoctorListener {
	public void doctorIsUp();
	public void hereIsJobStatus(JobsPackage jobsStatus);
}
