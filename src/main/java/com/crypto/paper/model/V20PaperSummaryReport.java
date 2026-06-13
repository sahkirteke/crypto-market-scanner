package com.crypto.paper.model;

public record V20PaperSummaryReport(
        V20PaperSummary longSummary,
        V20PaperSummary shortSummary,
        V20PaperSummary totalSummary
) {}
