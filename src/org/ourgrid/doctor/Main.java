package org.ourgrid.doctor;

import java.io.File;
import java.io.FileInputStream;

import org.apache.commons.io.IOUtils;


public class Main {
	
	public static void main(String[] args) throws Exception {
		Doctor doctor = new Doctor();
		doctor.start();
//		String content = IOUtils.toString(new FileInputStream(new File("report.txt")));
//		new EmailSender().send(content);
	}

}
