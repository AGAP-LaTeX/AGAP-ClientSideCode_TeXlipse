/* This file was generated by SableCC (http://www.sablecc.org/). */

package net.sourceforge.texlipse.bibparser.parser;

import net.sourceforge.texlipse.bibparser.node.*;
import net.sourceforge.texlipse.bibparser.analysis.*;

class TokenIndex extends AnalysisAdapter
{
    int index;

    @Override
    public void caseTTaskcomment(@SuppressWarnings("unused") TTaskcomment node)
    {
        this.index = 0;
    }

    @Override
    public void caseTEstring(@SuppressWarnings("unused") TEstring node)
    {
        this.index = 1;
    }

    @Override
    public void caseTScribeComment(@SuppressWarnings("unused") TScribeComment node)
    {
        this.index = 2;
    }

    @Override
    public void caseTPreamble(@SuppressWarnings("unused") TPreamble node)
    {
        this.index = 3;
    }

    @Override
    public void caseTEntryName(@SuppressWarnings("unused") TEntryName node)
    {
        this.index = 4;
    }

    @Override
    public void caseTLBrace(@SuppressWarnings("unused") TLBrace node)
    {
        this.index = 5;
    }

    @Override
    public void caseTRBrace(@SuppressWarnings("unused") TRBrace node)
    {
        this.index = 6;
    }

    @Override
    public void caseTBString(@SuppressWarnings("unused") TBString node)
    {
        this.index = 7;
    }

    @Override
    public void caseTLParen(@SuppressWarnings("unused") TLParen node)
    {
        this.index = 8;
    }

    @Override
    public void caseTRParen(@SuppressWarnings("unused") TRParen node)
    {
        this.index = 9;
    }

    @Override
    public void caseTComma(@SuppressWarnings("unused") TComma node)
    {
        this.index = 10;
    }

    @Override
    public void caseTEquals(@SuppressWarnings("unused") TEquals node)
    {
        this.index = 11;
    }

    @Override
    public void caseTSharp(@SuppressWarnings("unused") TSharp node)
    {
        this.index = 12;
    }

    @Override
    public void caseTNumber(@SuppressWarnings("unused") TNumber node)
    {
        this.index = 13;
    }

    @Override
    public void caseTIdentifier(@SuppressWarnings("unused") TIdentifier node)
    {
        this.index = 14;
    }

    @Override
    public void caseTQuotec(@SuppressWarnings("unused") TQuotec node)
    {
        this.index = 15;
    }

    @Override
    public void caseTStringLiteral(@SuppressWarnings("unused") TStringLiteral node)
    {
        this.index = 16;
    }

    @Override
    public void caseEOF(@SuppressWarnings("unused") EOF node)
    {
        this.index = 17;
    }
}
