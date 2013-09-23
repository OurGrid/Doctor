package org.ourgrid.doctor;

import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailSender {

	private Properties configuration;

	public EmailSender(Properties configuration) {
		this.configuration = configuration;
	}

	public void send(String content) throws MessagingException {
		String sender = configuration.getProperty(Conf.MAIL_SENDER);
		String recipients = configuration.getProperty(Conf.MAIL_RECIPIENT);
		String title = configuration.getProperty(Conf.MAIL_SUBJECT);
		
		Message message = new MimeMessage(getSMTPSession());
		message.setFrom(new InternetAddress(sender));
		message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipients));
		message.setSubject(title);
		content = "<html><body>" + content + "</body></html>";
		content = content.replaceAll("\n", "<br>");
		message.setContent(content, "text/html");
		Transport.send(message);
	}

	private Session getSMTPSession() {
		Properties smtpProps = new Properties();
		smtpProps.put("mail.transport.protocol", configuration.get(Conf.MAIL_PROTOCOL));
		smtpProps.put("mail.smtp.auth", Boolean.FALSE.toString());
		smtpProps.put("mail.smtp.host", configuration.get(Conf.MAIL_SMTP_HOST));
		Session session = Session.getInstance(smtpProps);
		return session;
	}
	
}
