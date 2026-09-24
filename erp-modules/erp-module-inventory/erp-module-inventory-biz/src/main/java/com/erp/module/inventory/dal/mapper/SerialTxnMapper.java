package com.erp.module.inventory.dal.mapper;

import com.erp.module.inventory.dal.dataobject.SerialTxnRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 序列号出入库记录（inv_serial_txn，只增） */
@Mapper
public interface SerialTxnMapper {

    @Insert("INSERT INTO inv_serial_txn (id, serial_id, txn_id, direction, doc_no, created_at) "
            + "VALUES (#{id}, #{serialId}, #{txnId}, #{direction}, #{docNo}, CURRENT_TIMESTAMP)")
    int insert(@Param("id") Long id, @Param("serialId") Long serialId, @Param("txnId") Long txnId, @Param("direction") String direction,
               @Param("docNo") String docNo);

    @Select("SELECT id, serial_id, txn_id, direction, doc_no, created_at FROM inv_serial_txn WHERE serial_id = #{serialId} ORDER BY created_at, id")
    List<SerialTxnRow> selectBySerial(@Param("serialId") Long serialId);
}
