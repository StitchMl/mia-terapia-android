package it.miaterapia.family.data
import androidx.room.TypeConverter
class Converters {
    @TypeConverter fun patientType(value: PatientType) = value.name
    @TypeConverter fun patientType(value: String) = PatientType.valueOf(value)
    @TypeConverter fun doseStatus(value: DoseStatus) = value.name
    @TypeConverter fun doseStatus(value: String) = DoseStatus.valueOf(value)
    @TypeConverter fun stockUnit(value: StockUnit) = value.name
    @TypeConverter fun stockUnit(value: String) = StockUnit.valueOf(value)
    @TypeConverter fun movementType(value: MovementType) = value.name
    @TypeConverter fun movementType(value: String) = MovementType.valueOf(value)
}
