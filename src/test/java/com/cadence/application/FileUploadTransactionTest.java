package com.cadence.application;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class FileUploadTransactionTest extends FileUploadTransactionContract {}
