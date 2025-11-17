package com.example.smartspendapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Generic reusable method to send any simple message.
     */
    public void sendSimpleMessage(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        System.out.println("📧 Email sent to: " + to);
    }

    /**
     * 🔹 Sends verification email using a link (legacy — you can still keep it).
     */
    public void sendVerificationEmail(String to, String link) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("SmartSpend - Verify your account");
        message.setText(
                "Welcome to SmartSpend!\n\n"
                + "Please verify your account using the link below:\n"
                + link + "\n\n"
                + "This link will expire in 24 hours.\n\n"
                + "Thank you!"
        );
        mailSender.send(message);
        System.out.println("📧 Verification email sent (link) to: " + to);
    }

    /**
     * 🔹 Sends verification email using an OTP (instead of link).
     * Use this method in your ApiAuthController instead of sendVerificationEmail().
     */
    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("SmartSpend - Your verification code");
        message.setText(
                "Welcome to SmartSpend!\n\n"
                + "Your one-time verification code is: " + otp + "\n\n"
                + "⚠️ This code will expire in 10 minutes.\n\n"
                + "If you didn’t request this, please ignore this message.\n\n"
                + "Thank you!"
        );
        mailSender.send(message);
        System.out.println("📧 OTP email sent to: " + to + " (OTP: " + otp + ")");
    }

    /**
     * 🔹 Sends a budget alert (near/exceeded) email.
     */
    public void sendBudgetAlert(String to, String category, double spent, double limit, boolean exceeded) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);

        String subject;
        String body;

        if (exceeded) {
            subject = "⚠️ Budget Exceeded: " + category;
            body = String.format(
                    "Hi!\n\nYou have exceeded your budget for %s.\n" +
                    "Spent: ₹%.2f / Limit: ₹%.2f\n\n" +
                    "Please review your expenses to get back on track.\n\n" +
                    "— SmartSpend Team",
                    category, spent, limit
            );
        } else {
            subject = "⚠️ Budget Nearing Limit: " + category;
            body = String.format(
                    "Hi!\n\nYou're nearing your budget for %s.\n" +
                    "Spent: ₹%.2f / Limit: ₹%.2f\n\n" +
                    "Keep an eye on your expenses to avoid overspending.\n\n" +
                    "— SmartSpend Team",
                    category, spent, limit
            );
        }

        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
        System.out.println("📧 Budget alert email sent to: " + to);
    }
}
