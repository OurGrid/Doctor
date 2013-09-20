package org.ourgrid.doctor;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailSender {

	public void send(String content) throws MessagingException {
		Message message = new MimeMessage(getSMTPSession());
		message.setFrom(new InternetAddress("ourgriddoctor@lsd.ufcg.edu.br"));
		message.setRecipients(Message.RecipientType.TO, 
				InternetAddress.parse("marcosnobregajr@gmail.com, abmargb@gmail.com"));
		message.setSubject("Doctor Report");
		content = "<html><body>" + content + "</body></html>";
		content = content.replaceAll("\n", "<br>");
		message.setContent(content, "text/html");
		Transport.send(message);
	}

	private Session getSMTPSession() {
		Properties smtpProps = new Properties();
		smtpProps.put("mail.transport.protocol", "smtp");
		smtpProps.put("mail.smtp.auth","false");
		smtpProps.put("mail.smtp.host","japones.lsd.ufcg.edu.br");
		Session session = Session.getInstance(smtpProps);
		return session;
	}
	
}
