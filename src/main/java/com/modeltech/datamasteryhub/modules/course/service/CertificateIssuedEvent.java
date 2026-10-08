package com.modeltech.datamasteryhub.modules.course.service;

/** Un certificat vient d'être délivré (publié dans la transaction de délivrance, traité après sa validation). */
public record CertificateIssuedEvent(String publicId) {}
