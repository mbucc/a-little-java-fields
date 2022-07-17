// Copyright 2022 Mark Bucciarelli <mkbucc@gmail.com>
// 
// Permission to use, copy, modify, and/or distribute this software for any
// purpose with or without fee is hereby granted, provided that the above
// copyright notice and this permission notice appear in all copies.
// 
// THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
// WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
// SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
// WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
// ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR
// IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class ALittleJava {

  static final String ORACLE_DATEFMT_US = "dd-MMM-yy";
  static final String POSTGRESQL_DATEFMT = "yyyy-MM-dd";


  interface FieldReducerI {
    Object forPrimaryKey(FieldD x);
    Object forField(FieldD x);
    Object forChangedTextField(FieldD x);
    Object forChangedDateField(FieldD x);
    Object forEndOfFields();
  }

  abstract class FieldD {
    abstract Object reduce(FieldReducerI ask);
  }

  class PrimaryKey extends FieldD {
    Field[] cols;
    FieldD next;
    PrimaryKey(Field[] _cols, FieldD _next) {
      cols = _cols;
      next = _next; }
    //---------------------------------------------------------
    public Object reduce(FieldReducerI ask) {
      return ask.forPrimaryKey(this); }
    public String toString() {
      return String.format("new %s(%s, %s)",
        getClass().getName(),
        Arrays.toString(cols),
        next); }
  }


  class Field extends FieldD {
    String name;
    String label;
    FieldD next;
    Field(String _name, String _label, FieldD _next) {
      name = _name;
      label = _label;
      next = _next; }
    //---------------------------------------------------------
    public Object reduce(FieldReducerI ask) {
      return ask.forField(this); }
    public String toString() {
      return String.format("new %s(\"%s\", \"%s\", %s)", getClass().getName(), name, label, next); }
  }
  class ChangedTextField extends FieldD {
    String name;
    String val;
    FieldD next;
    ChangedTextField(String _name, String _val, FieldD _next) {
      name = _name;
      val = _val;
      next = _next; }
    //---------------------------------------------------------
    public Object reduce(FieldReducerI ask) {
      return ask.forChangedTextField(this); }
    public String toString() {
      return String.format("new %s(\"%s\", \"%s\", %s)", getClass().getName(), name, val, next); }
  }

  // Default PostgreSQL date format is ISO (MDY).
  // ref: https://www.postgresql.org/docs/current/datatype-datetime.html#DATATYPE-DATETIME-INPUT
  // ref: https://www.postgresql.org/docs/current/runtime-config-client.html#GUC-DATESTYLE
  //
  // Default Oracle format may depend on the operating system.
  // If US, then format is 'dd-mon-rr' ('28-Feb-03').
  // ref: https://docs.oracle.com/database/121/NLSPG/ch3globenv.htm#NLSPG199
  // ref: https://docs.oracle.com/database/121/NLSPG/ch3globenv.htm#NLSPG203
  class ChangedDateField extends FieldD {
    String name;
    LocalDate val;
    FieldD next;
    ChangedDateField(String _name, LocalDate _val, FieldD _next) {
      name = _name;
      val = _val;
      next = _next; }
    //---------------------------------------------------------
    public Object reduce(FieldReducerI ask) {
      return ask.forChangedDateField(this); }
    public String toString() {
      return String.format("new %s(\"%s\", \"%s\", %s)", getClass().getName(), name, val, next); }
  }

  class EndOfFields extends FieldD {
    public Object reduce(FieldReducerI ask) {
      return ask.forEndOfFields(); }
    public String toString() {
      return "new " + getClass().getName() + "()"; }
  }

  class SelectSQLV implements FieldReducerI {
    String fields;
    String table;
    String where;
    SelectSQLV(String _fields, String _table, String _where) {
      fields = _fields;
      table = _table;
      where = _where; }
    //---------------------------------------------------------
    public Object forPrimaryKey(FieldD x) {
      where = "";
      for (Field c: ((PrimaryKey) x).cols) {
        if (!where.isEmpty())
          where += " AND ";
        where += ((Field) c).name + " = ?"; }
      return ((PrimaryKey) x).next.reduce(
          new SelectSQLV(fields, table, where)); }
    Object addFieldAndReduce(String name, FieldD next) {
      if (!fields.isEmpty())
         fields += ", ";
       fields += name;
       return next.reduce(new SelectSQLV(fields, table, where)); }
    public Object forField(FieldD x) {
      Field x1 = (Field) x;
      return addFieldAndReduce(x1.name, x1.next); }
    public Object forChangedTextField(FieldD x) {
      ChangedTextField x1 = (ChangedTextField) x;
      return addFieldAndReduce(x1.name, x1.next); }
    public Object forChangedDateField(FieldD x) {
      ChangedDateField x1 = (ChangedDateField) x;
      return addFieldAndReduce(x1.name, x1.next); }
    public Object forEndOfFields() {
      return this; }
    public String toString() {
      return String.format("new %s(\"%s\", \"%s\", \"%s\")",
        getClass().getName(),
        fields,
        table,
        where); }
    public String toSQL() {
      return String.format("SELECT %s FROM %s WHERE %s", fields, table, where); }
  }


  // https://docs.oracle.com/javase/tutorial/java/javaOO/nested.html
  class UpdateSQLV implements FieldReducerI {
    String fields;
    String table;
    String where;
    String dateFmt;
    UpdateSQLV(
        String _fields,
        String _table,
        String _where,
        String _dateFmt) {
      fields = _fields;
      table = _table;
      where = _where;
      dateFmt = _dateFmt; }
    //---------------------------------------------------------
    public Object forPrimaryKey(FieldD x) {
      where = "";
      for (Field c: ((PrimaryKey) x).cols) {
        if (!where.isEmpty())
          where += " AND ";
        where += ((Field) c).name + " = ?"; }
      return ((PrimaryKey) x).next.reduce(
          new UpdateSQLV(fields, table, where, dateFmt)); }
    public Object forField(FieldD x) {
      return ((Field) x).next.reduce(this); }
    public Object forChangedTextField(FieldD x) {
      ChangedTextField x1 = (ChangedTextField) x;
      if (!fields.isEmpty())
        fields += ", ";
      fields += String.format("%s = '%s'", x1.name, x1.val);
      return x1.next.reduce(new UpdateSQLV(fields, table, where, dateFmt)); }
    public Object forChangedDateField(FieldD x) {
      ChangedDateField x1 = (ChangedDateField) x;
      if (!fields.isEmpty())
        fields += ", ";
      fields += String.format("%s = '%s'"
        , x1.name
        , x1.val.atStartOfDay(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern(dateFmt)));
      return x1.next.reduce(new UpdateSQLV(fields, table, where, dateFmt)); }
    public Object forEndOfFields() {
      return this; }
    public String toString() {
      return String.format("new %s(\"%s\", \"%s\", \"%s\", \"%s\")",
        getClass().getName(),
        fields,
        table,
        where,
        dateFmt); }
    public String toSQL() {
      return String.format("UPDATE %s SET %s WHERE %s", table, fields, where); }
  }


  // https://docs.oracle.com/javase/tutorial/java/javaOO/nested.html
  class Main {
    public static void main(String[] args) {
      var x = new ALittleJava();
      FieldD y =
        x.new PrimaryKey(new Field[] {x.new Field("id", "Customer ID", null)},
          x.new Field("id", "Customer ID",
            x.new Field("name", "Customer Name",
              x.new ChangedTextField("email", "foo@bar.com",
                x.new EndOfFields()))));
      System.out.println("y = " + y);
      UpdateSQLV y1 = (UpdateSQLV) y.reduce(x.new UpdateSQLV("", "customer_t", "", ORACLE_DATEFMT_US));
      System.out.println("y1 = " + y1);
      System.out.println(y1.toSQL());
    }
  }
}
