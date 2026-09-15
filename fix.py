with open('app/src/main/java/com/example/viewmodel/PersonViewModel.kt', 'r') as f:
    content = f.read()
    
# Let's just find "fun deleteHousehold(" and "fun getHouseholdById" and replace between them
import re
match = re.search(r'    fun deleteHousehold.*?fun getHouseholdById', content, re.DOTALL)
if match:
    new_func = """    fun deleteHousehold(household: Household, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("PersonViewModel", "Starting delete household: id=${household.id}, uuid=${household.householdUuid}")
                
                // 1. Delete locally FIRST
                val result = repository.deleteHousehold(household)
                if (result.isFailure) {
                    val ex = result.exceptionOrNull()
                    withContext(Dispatchers.Main) {
                        onResult(false, ex?.message ?: "เกิดข้อผิดพลาดในการลบบ้าน")
                    }
                    return@launch
                }

                // 2. Queue for Cloud Sync and try immediate delete
                if (syncHelper != null && syncHelper.isFirebaseConfigured()) {
                    syncHelper.queueDeletion(household.householdUuid)
                    val cloudResult = syncHelper.deleteHouseholdFromFirestore(household.householdUuid)
                    if (cloudResult.isSuccess) {
                        syncHelper.removeDeletionQueue(household.householdUuid)
                    }
                }

                withContext(Dispatchers.Main) {
                    android.util.Log.d("PersonViewModel", "Household deleted successfully (Local)")
                    onResult(true, null)
                }
            } catch (e: Exception) {
                android.util.Log.e("PersonViewModel", "Exception deleting household", e)
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "เกิดข้อผิดพลาดที่ไม่คาดคิด")
                }
            }
        }
    }
    
    suspend fun getHouseholdById"""
    content = content[:match.start()] + new_func + content[match.end()-len("fun getHouseholdById"):]

match2 = re.search(r'    fun deletePerson.*?fun getPersonById', content, re.DOTALL)
if match2:
    new_func2 = """    fun deletePerson(person: Person, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Delete locally FIRST
                repository.delete(person)
                
                // 2. Try delete from Firestore, queue if failed
                if (syncHelper != null && syncHelper.isFirebaseConfigured()) {
                    syncHelper.queueDeletion(person.personUuid)
                    val cloudResult = syncHelper.deletePersonFromFirestore(person.personUuid)
                    if (cloudResult.isSuccess) {
                        syncHelper.removeDeletionQueue(person.personUuid)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    onResult(true, null)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onResult(false, e.message ?: "เกิดข้อผิดพลาดที่ไม่คาดคิด")
                }
            }
        }
    }
    
    suspend fun getPersonById"""
    content = content[:match2.start()] + new_func2 + content[match2.end()-len("fun getPersonById"):]

with open('app/src/main/java/com/example/viewmodel/PersonViewModel.kt', 'w') as f:
    f.write(content)
